package com.chengli.shop.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 库存变更类型。
 */
@Getter
@RequiredArgsConstructor
public enum InventoryChangeType {
    STOCK_IN(1, "入库"),
    ORDER_DEDUCT(2, "下单扣减"),
    ORDER_RETURN(3, "取消归还"),
    MANUAL_ADJUST(4, "人工调整");

    @EnumValue
    private final int code;
    private final String label;
}
