package com.chengli.shop.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单业务事件类型。
 */
@Getter
@RequiredArgsConstructor
public enum OrderEventType {
    CREATED("ORDER_CREATED", "订单创建"),
    PAID("ORDER_PAID", "订单支付"),
    CANCELLED("ORDER_CANCELLED", "订单取消");

    @EnumValue
    private final String code;
    private final String label;
}
