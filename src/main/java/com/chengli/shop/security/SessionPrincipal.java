package com.chengli.shop.security;

import java.io.Serializable;

/**
 * Session 中保存的不可变身份，只记录用户 ID；不保存密码、昵称或长期角色快照。
 * 每个已认证请求都会按此 ID 重新读取启用状态与角色。
 */
public record SessionPrincipal(Long userId) implements Serializable {

    public SessionPrincipal {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户 ID 必须为正整数");
        }
    }
}
