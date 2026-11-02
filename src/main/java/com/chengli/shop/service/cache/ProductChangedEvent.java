package com.chengli.shop.service.cache;

/** 只携带已保存商品的 ID，不传播未提交的详情对象。 */
public record ProductChangedEvent(Long productId) {
}
