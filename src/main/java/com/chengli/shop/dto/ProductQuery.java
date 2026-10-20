package com.chengli.shop.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductQuery {

    @Size(max = 120, message = "搜索关键词不能超过 120 个字符")
    private String keyword;

    @Positive(message = "分类 ID 必须为正整数")
    private Long categoryId;

    @Min(value = 0, message = "商品状态只能为 0 或 1")
    @Max(value = 1, message = "商品状态只能为 0 或 1")
    private Integer status;

    @NotNull
    @Min(value = 1, message = "页码不能小于 1")
    private Integer page = 1;

    @NotNull
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页最多 100 条")
    private Integer size = 10;

    @NotNull
    @Pattern(regexp = "createdAt|price|name", message = "排序字段只能为 createdAt、price 或 name")
    private String sortBy = "createdAt";

    @NotNull
    @Pattern(regexp = "asc|desc", message = "排序方向只能为 asc 或 desc")
    private String sortOrder = "desc";

}
