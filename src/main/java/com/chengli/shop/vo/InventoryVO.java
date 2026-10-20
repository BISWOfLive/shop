package com.chengli.shop.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class InventoryVO {
    private Long productId;
    private String productName;
    private String imageUrl;
    private Integer availableQty;
    private LocalDateTime updatedAt;
}
