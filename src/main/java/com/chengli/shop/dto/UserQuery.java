package com.chengli.shop.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/** 管理端用户列表参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQuery extends PageQuery {
    @Size(max = 64, message = "用户搜索关键词不能超过 64 个字符")
    private String keyword;

    @Min(value = 0, message = "账号状态只能为 0 或 1")
    @Max(value = 1, message = "账号状态只能为 0 或 1")
    private Integer status;
}
