package com.chengli.shop.service.cache;

import com.chengli.shop.vo.ProductDetailVO;
import com.chengli.shop.vo.ProductImageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProductDetailCacheCodec {
    // 复用 Boot 4 自动配置的 Jackson 3，保留 ID、金额和时间的现有注解。
    private final JsonMapper jsonMapper;

    public String encode(CachedProductDetail detail) {
        requireValid(detail);
        return jsonMapper.writeValueAsString(detail);
    }

    public CachedProductDetail decode(String json) {
        CachedProductDetail detail = jsonMapper.readValue(json, CachedProductDetail.class);
        requireValid(detail);
        return detail;
    }

    private void requireValid(CachedProductDetail detail) {
        if (detail == null || detail.schemaVersion() != CachedProductDetail.SCHEMA_VERSION
                || detail.missing() == null) {
            throw new IllegalArgumentException("商品详情缓存版本或信封无效");
        }
        if (detail.missing()) {
            if (detail.product() != null) {
                throw new IllegalArgumentException("空值缓存不能包含商品");
            }
            return;
        }
        ProductDetailVO product = detail.product();
        if (product == null || product.getId() == null || product.getId() <= 0
                || product.getName() == null || product.getSubtitle() == null
                || product.getCategoryId() == null || product.getCategoryName() == null
                || product.getPrice() == null || product.getDescription() == null
                || product.getImageUrl() == null || product.getStatus() == null
                || product.getCreatedAt() == null || product.getUpdatedAt() == null) {
            throw new IllegalArgumentException("商品详情缓存不完整");
        }
        requireImages(product.getCarouselImages(), product.getId(), 1);
        requireImages(product.getDetailImages(), product.getId(), 2);
    }

    private void requireImages(List<ProductImageVO> images, Long productId, int imageType) {
        if (images == null) {
            throw new IllegalArgumentException("商品详情缓存缺少图片集合");
        }
        for (ProductImageVO image : images) {
            if (image == null || image.getId() == null || !productId.equals(image.getProductId())
                    || !Integer.valueOf(imageType).equals(image.getImageType())
                    || image.getImageUrl() == null || image.getAltText() == null
                    || image.getSortOrder() == null || image.getSortOrder() < 0) {
                throw new IllegalArgumentException("商品详情缓存图片无效");
            }
        }
    }
}
