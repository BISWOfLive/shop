package com.chengli.shop.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/** 附图或详情图输入，所属商品 ID 由接口路径提供。 */
@Data
public class ProductImageSaveRequest {
    @NotBlank(message = "图片地址不能为空")
    @Size(max = 512, message = "图片地址不能超过 512 个字符")
    @Pattern(regexp = "^https?://[^\\s]+$", message = "图片地址须以 http:// 或 https:// 开头")
    private String imageUrl;

    @NotNull(message = "图片类型不能为空")
    @Min(value = 1, message = "图片类型只能为 1 或 2")
    @Max(value = 2, message = "图片类型只能为 1 或 2")
    private Integer imageType;

    @NotNull(message = "图片排序不能为空")
    @Min(value = 0, message = "图片排序不能小于 0")
    private Integer sortOrder = 0;

    @Size(max = 120, message = "图片说明不能超过 120 个字符")
    private String altText = "";
}
