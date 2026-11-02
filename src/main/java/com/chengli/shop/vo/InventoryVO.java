package com.chengli.shop.vo;
import lombok.Data;
import java.time.OffsetDateTime;
@Data
public class InventoryVO {
    private String productId;
    private String productName;
    private String imageUrl;
    private Integer availableQty;
    private OffsetDateTime updatedAt;
}
