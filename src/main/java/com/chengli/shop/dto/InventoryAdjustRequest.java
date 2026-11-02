package com.chengli.shop.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;

/** 有符号的库存人工调整；服务层负责保证调整后的库存不为负数。 */
@Data
public class InventoryAdjustRequest {
    @NotNull(message = "商品 ID 不能为空")
    @Positive(message = "商品 ID 必须为正整数")
    private Long productId;

    @NotBlank(message = "操作请求号不能为空")
    @Size(max = 64, message = "操作请求号不能超过 64 个字符")
    @Pattern(regexp = "[\\x21-\\x7E]+", message = "操作请求号仅支持无空格的可见 ASCII 字符")
    private String bizNo;

    @NotNull(message = "调整数量不能为空")
    private Integer changeQty;

    @Size(max = 255, message = "库存备注不能超过 255 个字符")
    private String remark = "";

    @AssertTrue(message = "调整数量不能为 0")
    public boolean isChangeNonZero() {
        return changeQty == null || changeQty != 0;
    }
}
