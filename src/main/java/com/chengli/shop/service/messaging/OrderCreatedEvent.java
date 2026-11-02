package com.chengli.shop.service.messaging;

public record OrderCreatedEvent(String eventId, String eventType, int payloadVersion,
                                String orderId, String orderNo, String buyerId, String expiresAt) {
}
