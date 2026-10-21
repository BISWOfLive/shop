package com.chengli.shop.vo;
import lombok.Data;
@Data
public class OrderItemVO {
    private String id;
    private String productId;
    private String productName;
    private String productImageUrl;
    private String unitPrice;
    private Integer quantity;
    private String lineAmount;
}
