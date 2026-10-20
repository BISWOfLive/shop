package com.chengli.shop.dto;

import lombok.Data;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 替换商品全部附图和详情图，空列表表示清空；不包含商品主图。 */
@Data
public class ProductImagesSaveRequest {
    @NotNull(message = "图片列表不能为空")
    @Size(max = 28, message = "附图与详情图合计最多 28 张")
    @Valid
    private List<@NotNull ProductImageSaveRequest> images = new ArrayList<>();

    @AssertTrue(message = "轮播附图最多 8 张，详情图最多 20 张")
    public boolean isImageCountWithinLimit() {
        if (images == null) {
            return true;
        }
        long carouselCount = images.stream().filter(Objects::nonNull)
                .filter(image -> Integer.valueOf(1).equals(image.getImageType())).count();
        long detailCount = images.stream().filter(Objects::nonNull)
                .filter(image -> Integer.valueOf(2).equals(image.getImageType())).count();
        return carouselCount <= 8 && detailCount <= 20;
    }
}
