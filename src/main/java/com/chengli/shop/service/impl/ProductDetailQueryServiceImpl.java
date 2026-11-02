package com.chengli.shop.service.impl;

import com.chengli.shop.config.ProductCacheProperties;
import com.chengli.shop.entity.Product;
import com.chengli.shop.exception.BusinessException;
import com.chengli.shop.mapper.ProductMapper;
import com.chengli.shop.observability.ShopBusinessMetrics;
import com.chengli.shop.service.ProductDetailQueryService;
import com.chengli.shop.service.ProductService;
import com.chengli.shop.service.cache.CachedProductDetail;
import com.chengli.shop.service.cache.ProductDetailCache;
import com.chengli.shop.vo.ProductDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 浏览详情入口。公开读取先按主库当前状态确认上架，再进入可选缓存；
 * 缓存命中与回源结果都拒绝非上架商品。管理员预览继续使用 /edit 直读。
 */
@Service
@RequiredArgsConstructor
public class ProductDetailQueryServiceImpl implements ProductDetailQueryService {

    private final ProductService productService;
    private final ProductMapper productMapper;
    private final ProductDetailCache cache;
    private final ProductCacheProperties properties;
    private final ShopBusinessMetrics metrics;

    @Override
    public ProductDetailVO getDetail(Long productId) {
        if (productId == null || productId <= 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "商品 ID 必须为正整数");
        }
        Product current = productMapper.selectById(productId);
        if (!isPublic(current)) {
            throw notFound();
        }
        if (!properties.isEnabled()) {
            metrics.recordCacheAccess("bypass");
            return requirePublic(productService.getDetail(productId));
        }

        Optional<CachedProductDetail> cached = cache.read(productId);
        if (cached.isPresent()) {
            CachedProductDetail detail = cached.get();
            if (detail.missing()) {
                metrics.recordCacheAccess("negative_hit");
                throw notFound();
            }
            // 命中内容也必须是上架商品；陈旧或损坏的内容不对外公开。
            if (!isPublic(detail.product())) {
                cache.evict(productId);
                throw notFound();
            }
            metrics.recordCacheAccess("hit");
            return detail.product();
        }
        metrics.recordCacheAccess("miss");

        ProductDetailVO product;
        try {
            // 经独立代理进入原只读事务，事务不跨越 Redis 访问。
            product = productService.getDetail(productId);
        } catch (BusinessException exception) {
            if (exception.getStatus() == HttpStatus.NOT_FOUND && "商品不存在".equals(exception.getMessage())) {
                cache.put(productId, CachedProductDetail.notFound(),
                        Duration.ofSeconds(properties.getNullTtlSeconds()));
            }
            throw exception;
        }
        requirePublic(product);
        long jitter = ThreadLocalRandom.current().nextLong((long) properties.getJitterSeconds() + 1);
        cache.put(productId, CachedProductDetail.present(product),
                Duration.ofSeconds((long) properties.getTtlSeconds() + jitter));
        return product;
    }

    private ProductDetailVO requirePublic(ProductDetailVO product) {
        if (!isPublic(product)) {
            throw notFound();
        }
        return product;
    }

    private boolean isPublic(ProductDetailVO product) {
        return product != null && Integer.valueOf(1).equals(product.getStatus());
    }

    private boolean isPublic(Product product) {
        return product != null && Integer.valueOf(1).equals(product.getStatus());
    }

    private BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
    }
}
