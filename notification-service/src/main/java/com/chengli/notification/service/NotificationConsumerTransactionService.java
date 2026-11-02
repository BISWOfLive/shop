package com.chengli.notification.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chengli.notification.entity.MessageConsumeRecord;
import com.chengli.notification.entity.Notification;
import com.chengli.notification.mapper.DatabaseTimeMapper;
import com.chengli.notification.mapper.MessageConsumeRecordMapper;
import com.chengli.notification.mapper.NotificationMapper;
import com.chengli.notification.messaging.OrderNotificationEvent;
import com.chengli.notification.messaging.OrderNotificationEventCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 通知消费的本地事务：notification 与 message_consume_record 同库同事务写入，
 * 任一失败一起回滚；成功记录只在业务事务提交后存在。重投冲突由外层用只读事务
 * 核对已提交记录与通知内容，不是把所有 DuplicateKeyException 都当作成功。
 */
@Service
@RequiredArgsConstructor
public class NotificationConsumerTransactionService {
    public static final String CONSUMER_NAME = "order-notification-v1";

    private final NotificationMapper notificationMapper;
    private final MessageConsumeRecordMapper recordMapper;
    private final DatabaseTimeMapper timeMapper;
    private final NotificationTextFactory textFactory;
    private final OrderNotificationEventCodec codec;

    @Transactional
    public void process(OrderNotificationEvent event) {
        codec.requireValid(event);
        var text = textFactory.create(event);
        var now = timeMapper.currentTime();
        var notification = new Notification();
        notification.setEventId(event.eventId());
        notification.setRecipientUserId(Long.valueOf(event.recipientUserId()));
        notification.setOrderNo(event.orderNo());
        notification.setTitle(text.title());
        notification.setContent(text.content());
        notification.setReadAt(null);
        // 通知落库时间取通知库时钟，不使用发布或消费途径的应用时间。
        notification.setCreatedAt(now);
        if (notificationMapper.insert(notification) != 1) {
            throw new IllegalStateException("通知写入失败");
        }
        var record = new MessageConsumeRecord();
        record.setConsumerName(CONSUMER_NAME);
        record.setEventId(event.eventId());
        record.setEventType(event.eventType());
        record.setProcessedAt(now);
        if (recordMapper.insert(record) != 1) {
            throw new IllegalStateException("消费成功记录写入失败");
        }
    }

    /**
     * 冲突回滚后读取另一个事务已提交的成功记录与对应通知；
     * 事件类型、接收人、订单号及固定模板内容全部一致才视为重复成功。
     */
    @Transactional(propagation = Propagation.NEVER, readOnly = true)
    public boolean alreadyProcessed(OrderNotificationEvent event) {
        var record = recordMapper.selectOne(Wrappers.<MessageConsumeRecord>lambdaQuery()
                .eq(MessageConsumeRecord::getConsumerName, CONSUMER_NAME)
                .eq(MessageConsumeRecord::getEventId, event.eventId()));
        if (record == null || !event.eventType().equals(record.getEventType())) {
            return false;
        }
        var notification = notificationMapper.selectOne(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getEventId, event.eventId())
                .eq(Notification::getRecipientUserId, Long.valueOf(event.recipientUserId())));
        if (notification == null) {
            return false;
        }
        var text = textFactory.create(event);
        return Objects.equals(event.orderNo(), notification.getOrderNo())
                && text.title().equals(notification.getTitle())
                && text.content().equals(notification.getContent());
    }
}
