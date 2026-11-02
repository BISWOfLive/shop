package com.chengli.shop.vo;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

@Data
public class ProductImageVO {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long productId;
    private String imageUrl;
    private Integer imageType;
    private Integer sortOrder;
    private String altText;
}
