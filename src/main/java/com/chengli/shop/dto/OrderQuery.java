package com.chengli.shop.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/** 当前用户的订单查询参数；买家 ID 从身份上下文获取。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderQuery extends PageQuery {
    @Size(max = 32, message = "订单号不能超过 32 个字符")
    private String orderNo;

    @Min(value = 0, message = "订单状态范围为 0–2")
    @Max(value = 2, message = "订单状态范围为 0–2")
    private Integer status;
}
