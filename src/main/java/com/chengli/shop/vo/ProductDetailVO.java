package com.chengli.shop.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

/** 商品详情扩展响应；主图沿用 ProductVO.imageUrl。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductDetailVO extends ProductVO {
    private List<ProductImageVO> carouselImages = List.of();
    private List<ProductImageVO> detailImages = List.of();
}
