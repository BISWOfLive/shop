package com.chengli.shop.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商品图片类型，主图单独保存。
 */
@Getter
@RequiredArgsConstructor
public enum ProductImageType {
    CAROUSEL(1, "轮播附图"),
    DETAIL(2, "详情图片");

    @EnumValue
    private final int code;
    private final String label;
}
