package com.chengli.shop.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/** 管理端账号状态参数。 */
@Data
public class UserStatusUpdateRequest {
    @NotNull(message = "账号状态不能为空")
    @Min(value = 0, message = "账号状态只能为 0 或 1")
    @Max(value = 1, message = "账号状态只能为 0 或 1")
    private Integer status;
}
