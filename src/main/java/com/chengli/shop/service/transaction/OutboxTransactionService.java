package com.chengli.shop.service.transaction;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chengli.shop.entity.OutboxEvent;
import com.chengli.shop.enums.OutboxPublishStatus;
import com.chengli.shop.mapper.OutboxEventMapper;
import com.chengli.shop.service.messaging.ClaimedOutboxEvent;
import com.chengli.shop.service.messaging.OutboxLeasePolicy;
import com.chengli.shop.service.messaging.PublishOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 共用 Outbox 租约事务服务：普通 Bean，不依赖单一模块开关、profile、网络连接或定时任务。
 * 每条发布通道必须显式传入允许的事件类型白名单与租约策略；候选查询和最终领取 UPDATE
 * 都带类型条件，拒绝空集合代表“全部事件”的隐式行为。
 */
@Service
@RequiredArgsConstructor
public class OutboxTransactionService {
    private final OutboxEventMapper mapper;

    public List<Long> candidateIds(Set<String> eventTypes) {
        requireEventTypes(eventTypes);
        var ids = new ArrayList<Long>(50);
        mapper.selectList(Wrappers.<OutboxEvent>lambdaQuery().select(OutboxEvent::getId)
                .in(OutboxEvent::getEventType, eventTypes)
                .eq(OutboxEvent::getPublishStatus, OutboxPublishStatus.PENDING.getCode())
                .apply("available_at <= CURRENT_TIMESTAMP(3)")
                .orderByAsc(OutboxEvent::getAvailableAt, OutboxEvent::getId).last("LIMIT 25"))
                .forEach(row -> ids.add(row.getId()));
        mapper.selectList(Wrappers.<OutboxEvent>lambdaQuery().select(OutboxEvent::getId)
                .in(OutboxEvent::getEventType, eventTypes)
                .eq(OutboxEvent::getPublishStatus, OutboxPublishStatus.PUBLISHING.getCode())
                .apply("locked_until <= CURRENT_TIMESTAMP(3)")
                .orderByAsc(OutboxEvent::getLockedUntil, OutboxEvent::getId).last("LIMIT 25"))
                .forEach(row -> ids.add(row.getId()));
        return ids.stream().distinct().toList();
    }

    @Transactional
    public Optional<ClaimedOutboxEvent> claim(Long id, String token, Set<String> eventTypes,
                                              OutboxLeasePolicy policy) {
        requireEventTypes(eventTypes);
        if (id == null || token == null || token.isBlank() || policy == null) {
            throw new IllegalArgumentException("Outbox 领取参数不完整");
        }
        int updated = mapper.update(null, Wrappers.<OutboxEvent>lambdaUpdate()
                .eq(OutboxEvent::getId, id)
                // 最终 UPDATE 也约束类型，防止未来调用方绕开候选筛选领取其他通道事件。
                .in(OutboxEvent::getEventType, eventTypes)
                .and(w -> w.eq(OutboxEvent::getPublishStatus, OutboxPublishStatus.PENDING.getCode())
                        .apply("available_at <= CURRENT_TIMESTAMP(3)")
                        .or(q -> q.eq(OutboxEvent::getPublishStatus, OutboxPublishStatus.PUBLISHING.getCode())
                                .apply("locked_until <= CURRENT_TIMESTAMP(3)")))
                .set(OutboxEvent::getPublishStatus, OutboxPublishStatus.PUBLISHING.getCode())
                .set(OutboxEvent::getLockedBy, token)
                .set(OutboxEvent::getPublishedAt, null)
                .setSql("locked_until = TIMESTAMPADD(SECOND, {0}, CURRENT_TIMESTAMP(3))", policy.leaseSeconds())
                .setSql("updated_at = CURRENT_TIMESTAMP(3)"));
        if (updated != 1) return Optional.empty();
        var row = mapper.selectById(id);
        return Optional.of(new ClaimedOutboxEvent(id, token, row.getEventId(), row.getEventType(),
                row.getPayloadVersion(), row.getPayload()));
    }

    @Transactional
    public boolean markPublished(Long id, String token) {
        return mapper.update(null, owned(id, token)
                .set(OutboxEvent::getPublishStatus, OutboxPublishStatus.PUBLISHED.getCode())
                .set(OutboxEvent::getLockedBy, null).set(OutboxEvent::getLockedUntil, null)
                .set(OutboxEvent::getLastError, "")
                .setSql("published_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)")) == 1;
    }

    @Transactional
    public boolean markFailed(Long id, String token, String error) {
        var row = mapper.selectOne(Wrappers.<OutboxEvent>lambdaQuery()
                .eq(OutboxEvent::getId, id)
                .eq(OutboxEvent::getPublishStatus, OutboxPublishStatus.PUBLISHING.getCode())
                .eq(OutboxEvent::getLockedBy, token).apply("locked_until > CURRENT_TIMESTAMP(3)")
                .last("FOR UPDATE"));
        if (row == null) return false;
        int attempts = Math.toIntExact((long) row.getRetryCount() + 1);
        int status = attempts >= 10 ? OutboxPublishStatus.STOPPED.getCode() : OutboxPublishStatus.PENDING.getCode();
        int delaySeconds = (int) Math.min(60L, 1L << Math.min(6, attempts - 1));
        return mapper.update(null, owned(id, token)
                .set(OutboxEvent::getPublishStatus, status).set(OutboxEvent::getRetryCount, attempts)
                .set(OutboxEvent::getLastError, PublishOutcome.summarize(error))
                .set(OutboxEvent::getLockedBy, null).set(OutboxEvent::getLockedUntil, null)
                .set(OutboxEvent::getPublishedAt, null)
                .setSql("available_at = TIMESTAMPADD(SECOND, {0}, CURRENT_TIMESTAMP(3))", delaySeconds)
                .setSql("updated_at = CURRENT_TIMESTAMP(3)")) == 1;
    }

    private LambdaUpdateWrapper<OutboxEvent> owned(Long id, String token) {
        return Wrappers.<OutboxEvent>lambdaUpdate().eq(OutboxEvent::getId, id)
                .eq(OutboxEvent::getPublishStatus, OutboxPublishStatus.PUBLISHING.getCode())
                .eq(OutboxEvent::getLockedBy, token).apply("locked_until > CURRENT_TIMESTAMP(3)");
    }

    /** 空白名单、空集合或空元素都拒绝；不用空集合隐式表示“全部事件”。 */
    private void requireEventTypes(Set<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()
                || eventTypes.stream().anyMatch(type -> type == null || type.isBlank())) {
            throw new IllegalArgumentException("Outbox 事件类型白名单不能为空");
        }
    }
}
