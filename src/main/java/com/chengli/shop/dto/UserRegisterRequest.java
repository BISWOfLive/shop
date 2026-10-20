package com.chengli.shop.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 注册输入；不允许客户端指定角色、账号状态或密码哈希。 */
@Data
public class UserRegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名不能超过 64 个字符")
    @Pattern(regexp = "[\\x21-\\x7E]+", message = "用户名仅支持无空格的可见 ASCII 字符")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 128, message = "密码长度须为 8–128 个字符")
    @ToString.Exclude
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Size(max = 64, message = "昵称不能超过 64 个字符")
    private String nickname = "";
}
