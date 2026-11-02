package com.chengli.shop.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 初始角色码。
 */
@Getter
@RequiredArgsConstructor
public enum RoleCode {
    ADMIN("ADMIN", "管理员"),
    USER("USER", "普通用户");

    @EnumValue
    private final String code;
    private final String label;
}
