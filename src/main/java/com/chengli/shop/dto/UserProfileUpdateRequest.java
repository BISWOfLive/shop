package com.chengli.shop.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 用户修改自身展示信息；用户 ID 由身份上下文获取。 */
@Data
public class UserProfileUpdateRequest {
    @NotNull(message = "昵称不能为空值")
    @Size(max = 64, message = "昵称不能超过 64 个字符")
    private String nickname = "";
}
