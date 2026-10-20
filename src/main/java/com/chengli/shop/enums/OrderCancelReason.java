package com.chengli.shop.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单取消原因。
 */
@Getter
@RequiredArgsConstructor
public enum OrderCancelReason {
    USER("USER", "主动取消"),
    TIMEOUT("TIMEOUT", "到期取消");

    @EnumValue
    private final String code;
    private final String label;
}
