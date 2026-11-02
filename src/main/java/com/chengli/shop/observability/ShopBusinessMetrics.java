package com.chengli.shop.observability;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chengli.shop.entity.OutboxEvent;
import com.chengli.shop.entity.ShopOrder;
import com.chengli.shop.enums.OutboxPublishStatus;
import com.chengli.shop.mapper.DatabaseTimeMapper;
import com.chengli.shop.mapper.OutboxEventMapper;
import com.chengli.shop.mapper.ShopOrderMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 有界周期采样的商城业务指标。数据库聚合只在独立调度线程按周期执行，
 * 不在每次健康探测或用户请求链路做全表统计；采样失败保留上次成功时间并置 stale。
 * 请求/消息标签只使用固定结果类型，不携带订单号、用户名、商品 ID 或 eventId。
 */
@Slf4j
@Component
public class ShopBusinessMetrics {

    private static final int STATUS_PENDING = OutboxPublishStatus.PENDING.getCode();
    private static final int STATUS_PUBLISHING = OutboxPublishStatus.PUBLISHING.getCode();
    private static final int STATUS_STOPPED = OutboxPublishStatus.STOPPED.getCode();
    private static final int ORDER_STATUS_PENDING_PAYMENT = 0;

    private final OutboxEventMapper outboxMapper;
    private final ShopOrderMapper orderMapper;
    private final DatabaseTimeMapper timeMapper;
    private final MeterRegistry registry;

    private final AtomicLong outboxPending = new AtomicLong();
    private final AtomicLong outboxPublishing = new AtomicLong();
    private final AtomicLong outboxStopped = new AtomicLong();
    private final AtomicLong notificationOutboxPending = new AtomicLong();
    private final AtomicLong outboxOldestPendingAgeSeconds = new AtomicLong(-1);
    private final AtomicLong timeoutDue = new AtomicLong();
    private final AtomicLong timeoutOldestDueAgeSeconds = new AtomicLong(-1);
    private final AtomicLong sampleTimestampSeconds = new AtomicLong(0);
    private final AtomicLong sampleStale = new AtomicLong(1);

    public ShopBusinessMetrics(OutboxEventMapper outboxMapper, ShopOrderMapper orderMapper,
                               DatabaseTimeMapper timeMapper, MeterRegistry registry) {
        this.outboxMapper = outboxMapper;
        this.orderMapper = orderMapper;
        this.timeMapper = timeMapper;
        this.registry = registry;
        gauge("shop.outbox.pending", "Outbox events waiting to publish", outboxPending);
        gauge("shop.outbox.publishing", "Outbox events currently leased", outboxPublishing);
        gauge("shop.outbox.stopped", "Outbox events stopped after retries", outboxStopped);
        gauge("shop.notification.outbox.pending",
                "ORDER_PAID/ORDER_CANCELLED events waiting to publish", notificationOutboxPending);
        gauge("shop.outbox.oldest.pending.age.seconds", "Oldest pending outbox age in seconds, -1 when none",
                outboxOldestPendingAgeSeconds);
        gauge("shop.order.timeout.due", "Pending-payment orders past their database expiry", timeoutDue);
        gauge("shop.order.timeout.oldest.due.age.seconds",
                "Oldest due order age in seconds, -1 when none", timeoutOldestDueAgeSeconds);
        gauge("shop.observability.sample.timestamp.seconds", "Last successful business sample epoch seconds",
                sampleTimestampSeconds);
        gauge("shop.observability.sample.stale", "1 when the last business sample failed", sampleStale);
    }

    private void gauge(String name, String description, AtomicLong value) {
        Gauge.builder(name, value, AtomicLong::get).description(description).register(this.registry);
    }

    @Scheduled(scheduler = "shopMetricsTaskScheduler",
            fixedDelayString = "${shop.observability.business-sample-millis:60000}",
            initialDelayString = "${shop.observability.business-sample-initial-delay-millis:30000}")
    public void sample() {
        try {
            LocalDateTime now = this.timeMapper.currentTime();
            this.outboxPending.set(this.outboxMapper.selectCount(Wrappers.<OutboxEvent>lambdaQuery()
                    .eq(OutboxEvent::getPublishStatus, STATUS_PENDING)));
            this.outboxPublishing.set(this.outboxMapper.selectCount(Wrappers.<OutboxEvent>lambdaQuery()
                    .eq(OutboxEvent::getPublishStatus, STATUS_PUBLISHING)));
            this.outboxStopped.set(this.outboxMapper.selectCount(Wrappers.<OutboxEvent>lambdaQuery()
                    .eq(OutboxEvent::getPublishStatus, STATUS_STOPPED)));
            // 通知通道积压单独采样：只统计终态通知类型，不把 ORDER_CREATED 算入通知告警。
            this.notificationOutboxPending.set(this.outboxMapper.selectCount(Wrappers.<OutboxEvent>lambdaQuery()
                    .in(OutboxEvent::getEventType, "ORDER_PAID", "ORDER_CANCELLED")
                    .eq(OutboxEvent::getPublishStatus, STATUS_PENDING)));
            OutboxEvent oldestPending = this.outboxMapper.selectOne(Wrappers.<OutboxEvent>lambdaQuery()
                    .select(OutboxEvent::getAvailableAt)
                    .eq(OutboxEvent::getPublishStatus, STATUS_PENDING)
                    .orderByAsc(OutboxEvent::getAvailableAt)
                    .last("LIMIT 1"));
            this.outboxOldestPendingAgeSeconds.set(ageSeconds(
                    oldestPending == null ? null : oldestPending.getAvailableAt(), now));

            this.timeoutDue.set(this.orderMapper.selectCount(Wrappers.<ShopOrder>lambdaQuery()
                    .eq(ShopOrder::getStatus, ORDER_STATUS_PENDING_PAYMENT)
                    .apply("expires_at <= CURRENT_TIMESTAMP(3)")));
            ShopOrder oldestDue = this.orderMapper.selectOne(Wrappers.<ShopOrder>lambdaQuery()
                    .select(ShopOrder::getExpiresAt)
                    .eq(ShopOrder::getStatus, ORDER_STATUS_PENDING_PAYMENT)
                    .apply("expires_at <= CURRENT_TIMESTAMP(3)")
                    .orderByAsc(ShopOrder::getExpiresAt)
                    .last("LIMIT 1"));
            this.timeoutOldestDueAgeSeconds.set(ageSeconds(
                    oldestDue == null ? null : oldestDue.getExpiresAt(), now));

            this.sampleTimestampSeconds.set(now.toEpochSecond(ZoneOffset.ofHours(8)));
            this.sampleStale.set(0);
        } catch (RuntimeException exception) {
            // 保留上次成功采样时间，只标记 stale；不打印异常消息，避免连接串或 SQL 进入日志。
            this.sampleStale.set(1);
            log.warn("业务指标采样失败 errorType={}", exception.getClass().getSimpleName());
        }
    }

    /** 商品详情缓存访问结果：hit / negative_hit / miss / bypass，由查询入口调用。 */
    public void recordCacheAccess(String result) {
        counter("shop.product_cache.access", result);
    }

    /** Outbox 发布结果：confirmed / failed / skipped / stale_lease / error。 */
    public void recordPublish(String result) {
        counter("shop.outbox.publish", result);
    }

    /** 到期消息消费结果：processed / duplicate / not_due / terminal_noop / invalid / failure。 */
    public void recordConsume(String result) {
        counter("shop.order.timeout.consume", result);
    }

    /** 扫描轮次候选数；只记录计数，不记录订单号。 */
    public void recordScanCandidates(int count) {
        counter("shop.order.timeout.scan.candidates", "candidates").increment(count);
    }

    /** 单次扫描取消失败数。 */
    public void recordScanFailure() {
        counter("shop.order.timeout.scan.failures", "failure").increment();
    }

    /** 单次扫描取消结果，取值为 TimeoutCancelResult 固定枚举名。 */
    public void recordScanResult(String result) {
        counter("shop.order.timeout.scan.results", result);
    }

    private io.micrometer.core.instrument.Counter counter(String name, String result) {
        return this.registry.counter(name, "result", result);
    }

    private long ageSeconds(LocalDateTime timestamp, LocalDateTime now) {
        if (timestamp == null) return -1;
        long seconds = Duration.between(timestamp, now).getSeconds();
        return Math.max(0, seconds);
    }
}
