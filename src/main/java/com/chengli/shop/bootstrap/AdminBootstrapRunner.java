package com.chengli.shop.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chengli.shop.config.BootstrapAdminProperties;
import com.chengli.shop.entity.Role;
import com.chengli.shop.entity.UserAccount;
import com.chengli.shop.entity.UserRole;
import com.chengli.shop.enums.AccountStatus;
import com.chengli.shop.mapper.RoleMapper;
import com.chengli.shop.mapper.UserAccountMapper;
import com.chengli.shop.mapper.UserRoleMapper;
import com.chengli.shop.service.account.AccountViews;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 仅 bootstrap-admin profile 与显式开关同时开启时创建第一名管理员。
 * 与账号维护使用相同锁顺序：先锁固定 ADMIN 角色行，再判断是否已有管理员授权。
 * 文件交付不运行本命令；实际账号创建由届时任务明确授权。
 */
@Slf4j
@Component
@Profile("bootstrap-admin")
@ConditionalOnProperty(prefix = "shop.bootstrap-admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final String USER_ROLE_CODE = "USER";

    private final BootstrapAdminProperties properties;
    private final UserAccountMapper accountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = AccountViews.normalizeUsername(properties.getUsername());
        String password = properties.getPassword();
        String nickname = AccountViews.normalizeNickname(properties.getNickname());
        if (!username.matches("[\\x21-\\x7E]{1,64}")) {
            throw new IllegalStateException("bootstrap 管理员用户名不合法");
        }
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new IllegalStateException("bootstrap 管理员密码长度须为 8–128 个字符");
        }
        if (nickname.length() > 64) {
            throw new IllegalStateException("bootstrap 管理员昵称不能超过 64 个字符");
        }

        // 与其他账号维护事务相同的固定锁顺序。
        Role adminRole = lockRole(ADMIN_ROLE_CODE);
        if (userRoleMapper.selectCount(Wrappers.<UserRole>lambdaQuery()
                .eq(UserRole::getRoleId, adminRole.getId())) > 0) {
            throw new IllegalStateException("已存在管理员授权，拒绝重复初始化");
        }
        if (accountMapper.selectCount(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getUsername, username)) > 0) {
            throw new IllegalStateException("用户名已存在，拒绝覆盖账号");
        }
        Role userRole = roleMapper.selectOne(Wrappers.<Role>lambdaQuery().eq(Role::getCode, USER_ROLE_CODE));
        if (userRole == null) {
            throw new IllegalStateException("USER 角色未初始化");
        }

        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setNickname(nickname);
        account.setStatus(AccountStatus.ENABLED.getCode());
        if (accountMapper.insert(account) != 1) {
            throw new IllegalStateException("管理员账号写入失败");
        }
        grant(account.getId(), adminRole.getId());
        grant(account.getId(), userRole.getId());
        log.info("已创建首个启用管理员 username={}", username);
    }

    private Role lockRole(String code) {
        Role role = roleMapper.selectOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getCode, code).last("FOR UPDATE"));
        if (role == null) {
            throw new IllegalStateException(code + " 角色未初始化");
        }
        return role;
    }

    private void grant(Long userId, Long roleId) {
        UserRole grant = new UserRole();
        grant.setUserId(userId);
        grant.setRoleId(roleId);
        if (userRoleMapper.insert(grant) != 1) {
            throw new IllegalStateException("管理员授权写入失败");
        }
    }
}
