package com.chengli.shop.service.cache;

import com.chengli.shop.vo.ProductDetailVO;

/** 仅供 Redis 使用的固定类型信封，不作为 HTTP 响应。 */
public record CachedProductDetail(int schemaVersion, Boolean missing, ProductDetailVO product) {
    public static final int SCHEMA_VERSION = 2;

    public static CachedProductDetail present(ProductDetailVO product) {
        return new CachedProductDetail(SCHEMA_VERSION, false, product);
    }

    public static CachedProductDetail notFound() {
        return new CachedProductDetail(SCHEMA_VERSION, true, null);
    }
}
