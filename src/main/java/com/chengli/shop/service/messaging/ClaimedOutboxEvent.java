package com.chengli.shop.service.messaging;

public record ClaimedOutboxEvent(Long id, String token, String eventId, String eventType,
                                 Integer payloadVersion, String payload) {
}
