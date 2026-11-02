package com.chengli.shop.service.messaging;

import com.chengli.shop.config.OrderTimeoutProperties;
import com.chengli.shop.entity.OutboxEvent;
import com.chengli.shop.entity.ShopOrder;
import com.chengli.shop.enums.OrderEventType;
import com.chengli.shop.enums.OutboxPublishStatus;
import com.chengli.shop.mapper.DatabaseTimeMapper;
import com.chengli.shop.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderCreatedEventRecorder {
    private final OrderTimeoutProperties properties;
    private final OutboxEventMapper mapper;
    private final DatabaseTimeMapper timeMapper;
    private final OrderEventCodec codec;

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(ShopOrder order) {
        if (!properties.isEnabled()) return;
        String eventId = UUID.randomUUID().toString();
        var event = new OrderCreatedEvent(eventId, OrderEventType.CREATED.getCode(), 1,
                order.getId().toString(), order.getOrderNo(), order.getBuyerId().toString(),
                order.getExpiresAt().atOffset(ZoneOffset.ofHours(8))
                        .format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSxxx")));
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
        if (mapper.insert(row) != 1) throw new IllegalStateException("订单创建事件写入失败");
    }
}