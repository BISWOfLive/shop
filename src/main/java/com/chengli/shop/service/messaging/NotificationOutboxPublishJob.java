package com.chengli.shop.service.messaging;

import com.chengli.shop.config.NotificationProperties;
import com.chengli.shop.config.NotificationRabbitConfiguration;
import com.chengli.shop.observability.ShopBusinessMetrics;
import com.chengli.shop.service.transaction.OutboxTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 通知发布通道：只领取 ORDER_PAID / ORDER_CANCELLED，租约提交后再解码、核对 Outbox
 * 元数据与 payload 并发送。关闭重试只表示发布停止，不改变订单终态。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "shop.notification", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class NotificationOutboxPublishJob {
    private static final Set<String> TERMINAL_TYPES = Set.of("ORDER_PAID", "ORDER_CANCELLED");

    private final OutboxTransactionService transactions;
    private final NotificationMessagePublisher publisher;
    private final OrderNotificationEventCodec codec;
    private final NotificationProperties properties;
    private final ShopBusinessMetrics metrics;

    @Scheduled(fixedDelayString = "${shop.notification.publish-poll-millis:1000}",
            scheduler = NotificationRabbitConfiguration.SCHEDULER)
    public void publishOnce() {
        var policy = new OutboxLeasePolicy(properties.getLeaseSeconds());
        for (Long id : transactions.candidateIds(TERMINAL_TYPES)) {
            if (Thread.currentThread().isInterrupted()) return;
            String token = UUID.randomUUID().toString();
            try {
                var claim = transactions.claim(id, token, TERMINAL_TYPES, policy);
                if (claim.isEmpty()) {
                    metrics.recordPublish("skipped");
                    continue;
                }
                // claim 的代理已经提交短事务，此处才执行可能阻塞的网络投递。
                var row = claim.get();
                MDC.put("eventId", row.eventId());
                try {
                    PublishOutcome outcome;
                    try {
                        var event = codec.decode(row.payload());
                        if (!Objects.equals(row.eventId(), event.eventId())
                                || !Objects.equals(row.eventType(), event.eventType())
                                || !Objects.equals(row.payloadVersion(), event.payloadVersion())) {
                            throw new IllegalArgumentException("Outbox 元数据与通知事件内容不符");
                        }
                        outcome = publisher.publish(event);
                    } catch (RuntimeException exception) {
                        outcome = PublishOutcome.failed(exception.getClass().getSimpleName() + ": " + exception.getMessage());
                    }
                    boolean marked;
                    if (outcome.status() == PublishOutcome.Status.CONFIRMED_ROUTED) {
                        marked = transactions.markPublished(id, token);
                        metrics.recordPublish(marked ? "confirmed" : "stale_lease");
                    } else {
                        marked = transactions.markFailed(id, token, outcome.error());
                        metrics.recordPublish(marked ? "failed" : "stale_lease");
                        log.warn("通知 Outbox 投递失败 id={} eventId={} error={}", id, row.eventId(), outcome.error());
                    }
                    if (!marked) log.info("丢弃过期租约回写 id={} eventId={} token={}", id, row.eventId(), token);
                } finally {
                    MDC.remove("eventId");
                }
            } catch (RuntimeException exception) {
                metrics.recordPublish("error");
                log.warn("通知 Outbox 任务失败 id={} token={}", id, token, exception);
            }
        }
    }
}
