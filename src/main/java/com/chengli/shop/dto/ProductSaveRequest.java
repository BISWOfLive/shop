package com.chengli.shop.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductSaveRequest {

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 120, message = "商品名称不能超过 120 个字符")
    private String name;

    @Size(max = 200, message = "商品短卖点不能超过 200 个字符")
    private String subtitle = "";

    @NotNull(message = "请选择商品分类")
    @Positive(message = "分类 ID 必须为正整数")
    private Long categoryId;

    @NotNull(message = "请填写商品价格")
    @DecimalMin(value = "0.01", message = "商品价格不能小于 0.01 元")
    @Digits(integer = 10, fraction = 2, message = "价格最多 10 位整数和 2 位小数")
    private BigDecimal price;

    @Size(max = 2000, message = "商品描述不能超过 2000 个字符")
    private String description = "";

    @Size(max = 512, message = "图片地址不能超过 512 个字符")
    @Pattern(regexp = "^(https?://[^\\s]+)?$", message = "图片地址必须以 http:// 或 https:// 开头")
    private String imageUrl = "";

    @NotNull(message = "请选择商品状态")
    @Min(value = 0, message = "商品状态只能为 0 或 1")
    @Max(value = 1, message = "商品状态只能为 0 或 1")
    private Integer status = 0;

    @NotNull(message = "请明确提交图片集合，无图请传空数组")
    @Size(max = 28, message = "附图与详情图合计最多 28 张")
    @Valid
    private List<@NotNull(message = "图片项不能为空") ProductImageSaveRequest> images;

    @AssertTrue(message = "上架商品必须填写主图地址")
    public boolean isPublishImagePresent() {
        return !Integer.valueOf(1).equals(status)
                || (imageUrl != null && !imageUrl.isBlank());
    }
}
