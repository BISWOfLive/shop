package com.chengli.shop.service.cache;

import com.chengli.shop.config.ProductCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisProductDetailCache implements ProductDetailCache {
    private final ProductCacheProperties properties;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ProductDetailCacheCodec codec;

    @Override
    public Optional<CachedProductDetail> read(Long productId) {
        if (!properties.isEnabled()) return Optional.empty();
        String key = key(productId);
        String json;
        try {
            json = redisTemplateProvider.getObject().opsForValue().get(key);
        } catch (RuntimeException exception) {
            logFailure("read", productId, key, exception);
            return Optional.empty();
        }
        if (json == null) return Optional.empty();
        try {
            CachedProductDetail detail = codec.decode(json);
            requireProductId(productId, detail);
            return Optional.of(detail);
        } catch (RuntimeException exception) {
            logFailure("decode", productId, key, exception);
            evict(productId);
            return Optional.empty();
        }
    }

    @Override
    public void put(Long productId, CachedProductDetail detail, Duration ttl) {
        if (!properties.isEnabled()) return;
        String key = key(productId);
        try {
            requireProductId(productId, detail);
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                throw new IllegalArgumentException("商品详情缓存必须设置正 TTL");
            }
            // 单条 SET 同时写入值和期限，不产生无过期时间的中间状态。
            redisTemplateProvider.getObject().opsForValue().set(key, codec.encode(detail), ttl);
        } catch (RuntimeException exception) {
            logFailure("put", productId, key, exception);
        }
    }

    @Override
    public void evict(Long productId) {
        if (!properties.isEnabled()) return;
        String key = key(productId);
        try {
            redisTemplateProvider.getObject().delete(key);
        } catch (RuntimeException exception) {
            logFailure("evict", productId, key, exception);
        }
    }

    private String key(Long productId) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("商品 ID 必须为正整数");
        }
        return properties.getNamespace() + ":product:detail:v2:" + productId;
    }

    private void requireProductId(Long productId, CachedProductDetail detail) {
        if (detail == null || (detail.product() != null && !productId.equals(detail.product().getId()))) {
            throw new IllegalArgumentException("商品详情缓存与键不匹配");
        }
    }

    private void logFailure(String operation, Long productId, String key, RuntimeException exception) {
        // 不输出异常消息、连接串或正文，避免把连接凭据写入日志。
        log.warn("Product detail cache failure: operation={}, productId={}, key={}, errorType={}",
                operation, productId, key, exception.getClass().getSimpleName());
    }
}
