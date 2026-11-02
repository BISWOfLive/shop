package com.chengli.shop.service.messaging;

import com.chengli.shop.config.NotificationProperties;
import com.chengli.shop.entity.OutboxEvent;
import com.chengli.shop.entity.ShopOrder;
import com.chengli.shop.enums.OrderCancelReason;
import com.chengli.shop.enums.OrderEventType;
import com.chengli.shop.enums.OutboxPublishStatus;
import com.chengli.shop.mapper.DatabaseTimeMapper;
import com.chengli.shop.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * 在真实终态转换事务中追加 ORDER_PAID / ORDER_CANCELLED Outbox。
 * MANDATORY 传播确保调用方已有订单本地事务；记录失败让该事务一起回滚，不吞掉事件。
 * 通知开关关闭时不追加任何终态事件。
 */
@Service
@RequiredArgsConstructor
public class OrderNotificationEventRecorder {
    private final NotificationProperties properties;
    private final OutboxEventMapper mapper;
    private final DatabaseTimeMapper timeMapper;
    private final OrderNotificationEventCodec codec;

    @Transactional(propagation = Propagation.MANDATORY)
    public void appendPaid(ShopOrder persistedOrder) {
        if (!properties.isEnabled()) return;
        requirePaid(persistedOrder);
        append(persistedOrder, OrderEventType.PAID.getCode(),
                codec.occurredAt(persistedOrder.getPaidAt()), null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void appendCancelled(ShopOrder persistedOrder) {
        if (!properties.isEnabled()) return;
        requireCancelled(persistedOrder);
        append(persistedOrder, OrderEventType.CANCELLED.getCode(),
                // 持锁订单的取消原因只有 USER / TIMEOUT；没有原因视为数据异常，必须回滚。
                codec.occurredAt(persistedOrder.getCancelledAt()), persistedOrder.getCancelReason());
    }

    private void append(ShopOrder order, String eventType, String occurredAt, String cancelReason) {
        String eventId = UUID.randomUUID().toString();
        var event = new OrderNotificationEvent(eventId, eventType, 1, order.getId().toString(),
                order.getOrderNo(), order.getBuyerId().toString(), amount(order.getTotalAmount()),
                occurredAt, cancelReason);
        codec.requireValid(event);
        var now = timeMapper.currentTime();
        var row = new OutboxEvent();
        row.setEventId(eventId);
        row.setAggregateType("ORDER");
        row.setAggregateId(order.getId());
        row.setEventType(event.eventType());
        row.setPayloadVersion(event.payloadVersion());
        row.setPayload(codec.encode(event));
        row.setPublishStatus(OutboxPublishStatus.PENDING.getCode());
        row.setRetryCount(0);
        row.setAvailableAt(now);
        row.setLockedBy(null);
        row.setLockedUntil(null);
        row.setPublishedAt(null);
        row.setLastError("");
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        if (mapper.insert(row) != 1) throw new IllegalStateException("订单终态通知事件写入失败");
    }

    private void requirePaid(ShopOrder order) {
        requireOrder(order);
        if (!Integer.valueOf(1).equals(order.getStatus()) || order.getPaidAt() == null) {
            throw new IllegalStateException("支付成功事件必须在已支付状态下追加");
        }
        if (order.getCancelledAt() != null || order.getCancelReason() != null) {
            throw new IllegalStateException("已支付订单不能保留取消字段");
        }
    }

    private void requireCancelled(ShopOrder order) {
        requireOrder(order);
        if (!Integer.valueOf(2).equals(order.getStatus()) || order.getCancelledAt() == null) {
            throw new IllegalStateException("取消事件必须在已取消状态下追加");
        }
        if (!OrderCancelReason.USER.getCode().equals(order.getCancelReason())
                && !OrderCancelReason.TIMEOUT.getCode().equals(order.getCancelReason())) {
            throw new IllegalStateException("取消事件原因只允许 USER 或 TIMEOUT");
        }
        if (order.getPaidAt() != null) {
            throw new IllegalStateException("已取消订单不能保留支付时间");
        }
    }

    private void requireOrder(ShopOrder order) {
        if (order == null || order.getId() == null || order.getId() <= 0
                || order.getBuyerId() == null || order.getBuyerId() <= 0
                || order.getOrderNo() == null || !order.getOrderNo().matches("[0-9a-f]{32}")) {
            throw new IllegalStateException("终态事件缺少已持久化订单身份");
        }
        amount(order.getTotalAmount());
    }

    private String amount(BigDecimal totalAmount) {
        if (totalAmount == null || totalAmount.signum() <= 0) {
            throw new IllegalStateException("终态事件订单金额必须为正数");
        }
        return totalAmount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
