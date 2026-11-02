package com.chengli.shop.service.messaging;

import com.chengli.shop.config.OrderTimeoutProperties;
import com.chengli.shop.observability.ShopBusinessMetrics;
import com.chengli.shop.service.transaction.OutboxTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 原订单到期通道：只领取 ORDER_CREATED，保持 v1 事件、队列与退避语义不变。
 * 新终态通知事件由独立的通知发布任务处理，两个通道只共享租约事务实现。
 */
@Slf4j
@Component
@Profile({"dev", "prod"})
@ConditionalOnProperty(prefix = "shop.order-timeout", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class OutboxPublishJob {
    private static final Set<String> CREATED_TYPES = Set.of("ORDER_CREATED");

    private final OutboxTransactionService transactions;
    private final OrderTimeoutMessagePublisher publisher;
    private final OrderEventCodec codec;
    private final ShopBusinessMetrics metrics;
    private final OrderTimeoutProperties properties;

    @Scheduled(fixedDelayString = "${shop.order-timeout.publish-poll-millis:1000}",
            scheduler = "orderTimeoutTaskScheduler")
    public void publishOnce() {
        var policy = new OutboxLeasePolicy(properties.getLeaseSeconds());
        for (Long id : transactions.candidateIds(CREATED_TYPES)) {
            if (Thread.currentThread().isInterrupted()) return;
            String token = UUID.randomUUID().toString();
            try {
                var claim = transactions.claim(id, token, CREATED_TYPES, policy);
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
                        var event = codec.decodeCreated(row.payload());
                        if (!Objects.equals(row.eventId(), event.eventId())
                                || !Objects.equals(row.eventType(), event.eventType())
                                || !Objects.equals(row.payloadVersion(), event.payloadVersion())) {
                            throw new IllegalArgumentException("Outbox 元数据与消息内容不符");
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
                        log.warn("Outbox 投递失败 id={} eventId={} error={}", id, row.eventId(), outcome.error());
                    }
                    if (!marked) log.info("丢弃过期租约回写 id={} eventId={} token={}", id, row.eventId(), token);
                } finally {
                    MDC.remove("eventId");
                }
            } catch (RuntimeException exception) {
                // 领取或回写失败保留数据库状态，发送中事件随后由租约回收。
                metrics.recordPublish("error");
                log.warn("Outbox 任务失败 id={} token={}", id, token, exception);
            }
        }
    }
}
