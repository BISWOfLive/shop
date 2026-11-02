package com.chengli.shop.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

/** 管理端角色字典输入。 */
@Data
public class RoleSaveRequest {
    @NotBlank(message = "角色码不能为空")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}", message = "角色码须由大写字母、数字和下划线组成，以字母开头")
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 64, message = "角色名称不能超过 64 个字符")
    private String name;
}
