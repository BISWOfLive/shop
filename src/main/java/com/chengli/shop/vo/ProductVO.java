package com.chengli.shop.vo;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductVO {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long id;
    private String name;
    private String subtitle;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long categoryId;
    private String categoryName;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal price;
    private String description;
    private String imageUrl;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
