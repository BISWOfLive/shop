package com.chengli.shop.service.cache;

import java.time.Duration;
import java.util.Optional;

/** 缓存故障由适配层吸收，数据库读取不属于此边界。 */
public interface ProductDetailCache {
    Optional<CachedProductDetail> read(Long productId);

    void put(Long productId, CachedProductDetail detail, Duration ttl);

    void evict(Long productId);
}
