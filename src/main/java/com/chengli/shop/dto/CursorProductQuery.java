package com.chengli.shop.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 仅用于全站上新和分类低价的连续浏览，不承担关键词查询或跳页。 */
@Data
public class CursorProductQuery {
    @NotNull
    @Pattern(regexp = "createdAt|price", message = "连续浏览排序只能为 createdAt 或 price")
    private String sortBy = "createdAt";

    @Positive(message = "分类 ID 必须为正整数")
    private Long categoryId;

    @NotNull
    @Min(value = 1, message = "每批至少 1 条")
    @Max(value = 100, message = "每批最多 100 条")
    private Integer size = 20;

    @Size(max = 512, message = "商品游标过长")
    private String cursor;

    @AssertTrue(message = "price 连续浏览必须指定分类，createdAt 连续浏览不能指定分类")
    public boolean isSupportedScenario() {
        return "price".equals(sortBy) ? categoryId != null : categoryId == null;
    }
}
