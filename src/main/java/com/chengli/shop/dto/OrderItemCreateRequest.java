package com.chengli.shop.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/** 下单商品输入；不接收客户端传入的价格和商品快照。 */
@Data
public class OrderItemCreateRequest {
    @NotNull(message = "商品 ID 不能为空")
    @Positive(message = "商品 ID 必须为正整数")
    private Long productId;

    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量不能小于 1")
    @Max(value = 9999, message = "单件商品购买数量不能超过 9999")
    private Integer quantity;
}
