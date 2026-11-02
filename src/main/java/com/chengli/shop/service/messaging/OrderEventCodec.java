package com.chengli.shop.service.messaging;

import com.chengli.shop.enums.OrderEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderEventCodec {
    private static final Set<String> FIELDS = Set.of("eventId", "eventType", "payloadVersion",
            "orderId", "orderNo", "buyerId", "expiresAt");
    private final JsonMapper jsonMapper;

    public String encode(OrderCreatedEvent event) {
        requireValid(event);
        return jsonMapper.writeValueAsString(event);
    }

    public OrderCreatedEvent decodeCreated(String json) {
        try {
            // 固定 JSON 对象，不启用多态类型；ID 必须原本就是字符串，不能靠反序列化强制转换。
            Map<?, ?> value = jsonMapper.readValue(json, Map.class);
            if (value == null || !FIELDS.equals(value.keySet()) || !Integer.valueOf(1).equals(value.get("payloadVersion"))) {
                throw new IllegalArgumentException("订单事件字段或版本无效");
            }
            var event = new OrderCreatedEvent(text(value, "eventId"), text(value, "eventType"), 1,
                    text(value, "orderId"), text(value, "orderNo"), text(value, "buyerId"), text(value, "expiresAt"));
            requireValid(event);
            return event;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("ORDER_CREATED v1 事件无效", exception);
        }
    }

    public void requireValid(OrderCreatedEvent event) {
        if (event == null || !OrderEventType.CREATED.getCode().equals(event.eventType()) || event.payloadVersion() != 1
                || event.eventId() == null || !UUID.fromString(event.eventId()).toString().equals(event.eventId())
                || event.orderNo() == null || !event.orderNo().matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("订单事件类型、版本或业务标识无效");
        }
        requireId(event.orderId());
        requireId(event.buyerId());
        if (event.expiresAt() == null
                || !ZoneOffset.ofHours(8).equals(OffsetDateTime.parse(event.expiresAt()).getOffset())) {
            throw new IllegalArgumentException("订单事件时间必须带 +08:00 偏移");
        }
    }

    private String text(Map<?, ?> value, String field) {
        if (value.get(field) instanceof String text) return text;
        throw new IllegalArgumentException("订单事件字段必须为字符串：" + field);
    }

    private void requireId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,18}") || Long.parseLong(id) <= 0) {
            throw new IllegalArgumentException("订单事件 ID 无效");
        }
    }
}
