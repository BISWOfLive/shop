package com.chengli.shop.service.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chengli.shop.entity.ShopOrder;
import com.chengli.shop.mapper.ShopOrderMapper;
import com.chengli.shop.observability.ShopBusinessMetrics;
import com.chengli.shop.service.transaction.OrderTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.EnumMap;

@Slf4j
@Component
@Profile({"dev", "prod"})
@ConditionalOnProperty(prefix = "shop.order-timeout", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class OrderTimeoutScanJob {
    private final ShopOrderMapper mapper;
    private final OrderTransactionService orders;
    private final ShopBusinessMetrics metrics;
    private LocalDateTime cursorExpiresAt;
    private Long cursorId;

    @Scheduled(fixedDelayString = "${shop.order-timeout.scan-poll-millis:30000}",
            scheduler = "orderTimeoutTaskScheduler")
    public synchronized void scanOnce() {
        var query = Wrappers.<ShopOrder>lambdaQuery()
                .select(ShopOrder::getId, ShopOrder::getOrderNo, ShopOrder::getExpiresAt)
                .eq(ShopOrder::getStatus, 0).apply("expires_at <= CURRENT_TIMESTAMP(3)");
        if (cursorExpiresAt != null) {
            query.and(w -> w.gt(ShopOrder::getExpiresAt, cursorExpiresAt)
                    .or(q -> q.eq(ShopOrder::getExpiresAt, cursorExpiresAt).gt(ShopOrder::getId, cursorId)));
        }
        var candidates = mapper.selectList(query.orderByAsc(ShopOrder::getExpiresAt, ShopOrder::getId).last("LIMIT 100"));
        metrics.recordScanCandidates(candidates.size());
        if (candidates.isEmpty()) {
            cursorExpiresAt = null;
            cursorId = null;
            return;
        }
        var results = new EnumMap<TimeoutCancelResult, Integer>(TimeoutCancelResult.class);
        int failed = 0;
        for (var order : candidates) {
            try {
                MDC.put("orderNo", order.getOrderNo());
                // 扫描自身无事务，每单通过代理独立提交；不读取消息发布状态或缓存。
                var result = orders.cancelExpired(order.getOrderNo());
                results.merge(result, 1, Integer::sum);
                metrics.recordScanResult(result.name());
            } catch (RuntimeException exception) {
                failed++;
                metrics.recordScanFailure();
                log.warn("到期扫描取消失败 orderNo={}", order.getOrderNo(), exception);
            } finally {
                MDC.remove("orderNo");
            }
            // 失败者也推进位置，后续轮次回绕后再次尝试，避免坏单挡住积压。
            cursorExpiresAt = order.getExpiresAt();
            cursorId = order.getId();
        }
        if (candidates.size() < 100) {
            cursorExpiresAt = null;
            cursorId = null;
        }
        log.info("到期扫描 candidates={} results={} failed={}", candidates.size(), results, failed);
    }
}
