package com.chengli.shop.service.account;

import com.chengli.shop.entity.Role;
import com.chengli.shop.entity.UserAccount;
import com.chengli.shop.vo.RoleVO;
import com.chengli.shop.vo.UserVO;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/** 账号、角色到响应的集中映射：字符串 ID、带 +08:00 偏移的业务时间。 */
public final class AccountViews {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private AccountViews() {
    }

    public static UserVO toUserVO(UserAccount account, List<RoleVO> roles) {
        return toUserVO(account, roles, false);
    }

    /**
     * @param notificationsEnabled 当前部署是否启用站内通知能力；只作为前端入口提示，
     *                             不代替每次请求的服务端授权。
     */
    public static UserVO toUserVO(UserAccount account, List<RoleVO> roles, boolean notificationsEnabled) {
        if (account == null) {
            throw new IllegalArgumentException("账号不能为空");
        }
        UserVO vo = new UserVO();
        vo.setId(account.getId().toString());
        vo.setUsername(account.getUsername());
        vo.setNickname(account.getNickname() == null ? "" : account.getNickname());
        vo.setStatus(account.getStatus());
        vo.setRoles(roles == null ? List.of() : List.copyOf(roles));
        vo.setCreatedAt(toResponseTime(account.getCreatedAt()));
        vo.setUpdatedAt(toResponseTime(account.getUpdatedAt()));
        vo.getFeatures().setNotifications(notificationsEnabled);
        return vo;
    }

    public static RoleVO toRoleVO(Role role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId().toString());
        vo.setCode(role.getCode());
        vo.setName(role.getName());
        return vo;
    }

    public static OffsetDateTime toResponseTime(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    /** 用户名存储与查询统一：去首尾空白后转 ASCII 小写。 */
    public static String normalizeUsername(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    /** 昵称只做首尾空白规范化，不改变大小写。 */
    public static String normalizeNickname(String value) {
        return value == null ? "" : value.strip();
    }
}
