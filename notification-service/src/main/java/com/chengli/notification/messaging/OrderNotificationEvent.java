package com.chengli.notification.messaging;

/**
 * 订单终态通知事件 v1 的不可变快照。通知服务显式维护自己的协议结构，
 * 不依赖商城工程 jar、订单实体或 ORM 序列化。
 */
public record OrderNotificationEvent(String eventId, String eventType, int payloadVersion,
                                     String orderId, String orderNo, String recipientUserId,
                                     String totalAmount, String occurredAt, String cancelReason) {
}
