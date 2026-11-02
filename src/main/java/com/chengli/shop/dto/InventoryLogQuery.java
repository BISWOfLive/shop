package com.chengli.shop.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

/** 库存流水列表参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InventoryLogQuery extends PageQuery {
    @Positive(message = "商品 ID 必须为正整数")
    private Long productId;

    @Positive(message = "订单 ID 必须为正整数")
    private Long orderId;

    @Min(value = 1, message = "库存变更类型范围为 1–4")
    @Max(value = 4, message = "库存变更类型范围为 1–4")
    private Integer changeType;

    @Size(max = 64, message = "业务号不能超过 64 个字符")
    private String bizNo;
}
