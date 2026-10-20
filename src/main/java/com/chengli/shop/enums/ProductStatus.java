package com.chengli.shop.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商品上下架状态。
 */
@Getter
@RequiredArgsConstructor
public enum ProductStatus {
    OFF_SHELF(0, "下架"),
    ON_SHELF(1, "上架");

    @EnumValue
    private final int code;
    private final String label;
}
