package com.chengli.shop.vo;

/** 连续浏览只传卡片字段，ID 与金额在响应边界转换为字符串。 */
public record ProductCardVO(String id, String name, String subtitle, String categoryId,
                            String categoryName, String price, String imageUrl) {
}
