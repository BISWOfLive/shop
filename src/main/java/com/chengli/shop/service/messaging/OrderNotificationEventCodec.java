package com.chengli.shop.service.messaging;

import com.chengli.shop.enums.OrderCancelReason;
import com.chengli.shop.enums.OrderEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ORDER_PAID / ORDER_CANCELLED v1 事件编解码。与只识别 ORDER_CREATED 的旧编码器相互独立，
 * 固定字段集合、类型、版本和数值范围，不执行多态反序列化或按类名实例化。
 */
@Component
@RequiredArgsConstructor
public class OrderNotificationEventCodec {
    private static final Set<String> FIELDS = Set.of("eventId", "eventType", "payloadVersion",
            "orderId", "orderNo", "recipientUserId", "totalAmount", "occurredAt", "cancelReason");
    private static final ZoneOffset BUSINESS_OFFSET = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter OCCURRED_AT_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSxxx");
    private static final String AMOUNT_PATTERN = "[1-9][0-9]{0,15}\\.[0-9]{2}";
    private final JsonMapper jsonMapper;

    public String encode(OrderNotificationEvent event) {
        requireValid(event);
        return jsonMapper.writeValueAsString(event);
    }

    public OrderNotificationEvent decode(String json) {
        try {
            // 固定 JSON 对象，不启用多态类型；ID 与金额必须原本就是字符串，不能靠反序列化强制转换。
            Map<?, ?> value = jsonMapper.readValue(json, Map.class);
            if (value == null || !FIELDS.equals(value.keySet())) {
                throw new IllegalArgumentException("通知事件字段集合无效");
            }
            Object version = value.get("payloadVersion");
            if (!(version instanceof Integer payloadVersion) || payloadVersion != 1) {
                throw new IllegalArgumentException("通知事件版本无效");
            }
            Object cancelReason = value.get("cancelReason");
            if (cancelReason != null && !(cancelReason instanceof String)) {
                throw new IllegalArgumentException("通知事件取消原因必须为字符串或 null");
            }
            var event = new OrderNotificationEvent(text(value, "eventId"), text(value, "eventType"),
                    payloadVersion, text(value, "orderId"), text(value, "orderNo"),
                    text(value, "recipientUserId"), text(value, "totalAmount"),
                    text(value, "occurredAt"), (String) cancelReason);
            requireValid(event);
            return event;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("订单终态通知 v1 事件无效", exception);
        }
    }

    public void requireValid(OrderNotificationEvent event) {
        if (event == null || event.payloadVersion() != 1
                || event.eventId() == null || !canonicalUuid(event.eventId())) {
            throw new IllegalArgumentException("通知事件类型、版本或事件 ID 无效");
        }
        boolean paid = OrderEventType.PAID.getCode().equals(event.eventType());
        boolean cancelled = OrderEventType.CANCELLED.getCode().equals(event.eventType());
        if (!paid && !cancelled) {
            throw new IllegalArgumentException("通知事件只允许 ORDER_PAID 或 ORDER_CANCELLED");
        }
        requireId(event.orderId());
        requireId(event.recipientUserId());
        if (event.orderNo() == null || !event.orderNo().matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("通知事件订单号无效");
        }
        requireAmount(event.totalAmount());
        requireOccurredAt(event.occurredAt());
        if (paid) {
            if (event.cancelReason() != null) {
                throw new IllegalArgumentException("支付成功事件不能带取消原因");
            }
        } else if (!OrderCancelReason.USER.getCode().equals(event.cancelReason())
                && !OrderCancelReason.TIMEOUT.getCode().equals(event.cancelReason())) {
            throw new IllegalArgumentException("取消事件原因只允许 USER 或 TIMEOUT");
        }
    }

    public static String occurredAt(java.time.LocalDateTime value) {
        if (value == null) throw new IllegalArgumentException("通知事件发生时间不能为空");
        return value.atOffset(BUSINESS_OFFSET).format(OCCURRED_AT_FORMAT);
    }

    private boolean canonicalUuid(String value) {
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void requireId(String id) {
        if (id == null || !id.matches("[1-9][0-9]{0,18}") || Long.parseLong(id) <= 0) {
            throw new IllegalArgumentException("通知事件 ID 无效");
        }
    }

    private void requireAmount(String amount) {
        if (amount == null || !amount.matches(AMOUNT_PATTERN)) {
            throw new IllegalArgumentException("通知事件金额必须为两位小数的正数");
        }
        BigDecimal value = new BigDecimal(amount);
        if (value.signum() <= 0 || value.scale() != 2 || value.precision() > 18) {
            throw new IllegalArgumentException("通知事件金额超出 DECIMAL(18,2) 范围");
        }
    }

    private void requireOccurredAt(String occurredAt) {
        if (occurredAt == null) throw new IllegalArgumentException("通知事件发生时间不能为空");
        OffsetDateTime parsed = OffsetDateTime.parse(occurredAt);
        if (!BUSINESS_OFFSET.equals(parsed.getOffset())) {
            throw new IllegalArgumentException("通知事件时间必须带 +08:00 偏移");
        }
    }

    private String text(Map<?, ?> value, String field) {
        if (value.get(field) instanceof String text) return text;
        throw new IllegalArgumentException("通知事件字段必须为字符串：" + field);
    }
}
