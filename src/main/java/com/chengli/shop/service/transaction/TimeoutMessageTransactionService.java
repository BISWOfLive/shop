package com.chengli.shop.service.transaction;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chengli.shop.config.OrderRabbitConfiguration;
import com.chengli.shop.entity.MessageConsumeRecord;
import com.chengli.shop.entity.ShopOrder;
import com.chengli.shop.mapper.DatabaseTimeMapper;
import com.chengli.shop.mapper.MessageConsumeRecordMapper;
import com.chengli.shop.mapper.ShopOrderMapper;
import com.chengli.shop.service.messaging.OrderCreatedEvent;
import com.chengli.shop.service.messaging.OrderEventCodec;
import com.chengli.shop.service.messaging.TimeoutMessageResult;
import com.chengli.shop.service.order.TimeoutCancelResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"dev", "prod"})
@ConditionalOnProperty(prefix = "shop.order-timeout", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class TimeoutMessageTransactionService {
    private final ShopOrderMapper orderMapper;
    private final MessageConsumeRecordMapper recordMapper;
    private final DatabaseTimeMapper timeMapper;
    private final OrderTransactionService orders;
    private final OrderEventCodec codec;

    @Transactional
    public TimeoutMessageResult process(OrderCreatedEvent event) {
        codec.requireValid(event);
        var order = orderMapper.selectOne(Wrappers.<ShopOrder>lambdaQuery()
                .eq(ShopOrder::getId, Long.valueOf(event.orderId())).last("FOR UPDATE"));
        if (order == null || !order.getOrderNo().equals(event.orderNo())
                || !order.getBuyerId().equals(Long.valueOf(event.buyerId()))) {
            throw new IllegalArgumentException("订单事件与主库订单身份不符");
        }
        var result = orders.cancelExpired(event.orderNo());
        if (result == TimeoutCancelResult.NOT_DUE) return TimeoutMessageResult.NOT_DUE;
        if (result == TimeoutCancelResult.NOT_FOUND) throw new IllegalArgumentException("订单事件对应订单不存在");
        var record = new MessageConsumeRecord();
        record.setConsumerName(OrderRabbitConfiguration.CONSUMER_NAME);
        record.setEventId(event.eventId());
        record.setEventType(event.eventType());
        record.setProcessedAt(timeMapper.currentTime());
        // 重复键必须穿过事务代理回滚；外层随后读取另一个事务已提交的成功记录。
        if (recordMapper.insert(record) != 1) throw new IllegalStateException("消费成功记录写入失败");
        return result == TimeoutCancelResult.CANCELLED ? TimeoutMessageResult.PROCESSED : TimeoutMessageResult.TERMINAL_NOOP;
    }

    @Transactional(propagation = Propagation.NEVER, readOnly = true)
    public boolean alreadyProcessed(OrderCreatedEvent event) {
        var record = recordMapper.selectOne(Wrappers.<MessageConsumeRecord>lambdaQuery()
                .eq(MessageConsumeRecord::getConsumerName, OrderRabbitConfiguration.CONSUMER_NAME)
                .eq(MessageConsumeRecord::getEventId, event.eventId()));
        return record != null && event.eventType().equals(record.getEventType());
    }
}
