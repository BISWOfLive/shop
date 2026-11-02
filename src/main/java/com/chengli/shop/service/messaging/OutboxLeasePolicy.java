package com.chengli.shop.service.messaging;

/**
 * 每次 Outbox 领取使用的租约时长，由各发布通道显式传入，
 * 不让共用事务服务隐式依赖某个模块的配置或调度器。
 */
public record OutboxLeasePolicy(int leaseSeconds) {

    public OutboxLeasePolicy {
        if (leaseSeconds < 1 || leaseSeconds > 3600) {
            throw new IllegalArgumentException("Outbox 租约秒数必须为 1～3600");
        }
    }
}
