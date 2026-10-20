package com.chengli.shop.vo;

import lombok.Data;

@Data
public class ProductImageVO {
    private Long id;
    private Long productId;
    private String imageUrl;
    private Integer imageType;
    private Integer sortOrder;
    private String altText;
}
