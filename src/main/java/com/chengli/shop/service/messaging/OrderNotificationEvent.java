package com.chengli.shop.service.messaging;

/**
 * 订单终态通知事件 v1 的不可变快照。字段来自已提交订单，不携带账号资料或完整订单对象；
 * 编码直接使用本记录，不依赖 ORM 实体序列化。
 */
public record OrderNotificationEvent(String eventId, String eventType, int payloadVersion,
                                     String orderId, String orderNo, String recipientUserId,
                                     String totalAmount, String occurredAt, String cancelReason) {
}
