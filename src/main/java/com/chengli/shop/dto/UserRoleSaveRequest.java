package com.chengli.shop.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Set;

/** 管理端替换用户角色，空集合表示撤销全部角色。 */
@Data
public class UserRoleSaveRequest {
    @NotNull(message = "用户 ID 不能为空")
    @Positive(message = "用户 ID 必须为正整数")
    private Long userId;

    @NotNull(message = "角色集合不能为空值")
    private Set<@NotNull @Positive Long> roleIds;
}
