package com.chengli.shop.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chengli.shop.entity.Role;
import com.chengli.shop.entity.UserAccount;
import com.chengli.shop.entity.UserRole;
import com.chengli.shop.enums.AccountStatus;
import com.chengli.shop.mapper.RoleMapper;
import com.chengli.shop.mapper.UserAccountMapper;
import com.chengli.shop.mapper.UserRoleMapper;
import com.chengli.shop.service.account.AccountViews;
import com.chengli.shop.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 账号加载与角色读取的统一入口：登录认证、每请求身份刷新和账号管理共用同一查询口径。
 */
@Component
@RequiredArgsConstructor
public class AccountUserDetailsService implements UserDetailsService {

    private final UserAccountMapper accountMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount account = accountMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getUsername, AccountViews.normalizeUsername(username)));
        if (account == null) {
            throw new UsernameNotFoundException("用户名或密码错误");
        }
        String passwordHash = account.getPasswordHash();
        // 无口令或被禁用的占位账号不能登录；账号是否存在不在响应中区分。
        boolean usable = Integer.valueOf(AccountStatus.ENABLED.getCode()).equals(account.getStatus())
                && passwordHash != null && !passwordHash.isBlank();
        return new AccountUserDetails(account.getId(), account.getUsername(), passwordHash, usable,
                loadAuthorities(account.getId()));
    }

    public UserAccount loadAccount(Long userId) {
        return userId == null ? null : accountMapper.selectById(userId);
    }

    public List<GrantedAuthority> loadAuthorities(Long userId) {
        List<Long> roleIds = userRoleMapper.selectList(Wrappers.<UserRole>lambdaQuery()
                        .eq(UserRole::getUserId, userId))
                .stream().map(UserRole::getRoleId).distinct().toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectList(Wrappers.<Role>lambdaQuery()
                        .in(Role::getId, roleIds).orderByAsc(Role::getId))
                .stream()
                .filter(role -> role.getCode() != null && !role.getCode().isBlank())
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.getCode()))
                .toList();
    }

    public List<RoleVO> loadRoleViews(Long userId) {
        return loadRoleViews(List.of(userId)).getOrDefault(userId, List.of());
    }

    /** 一次批量读取多个用户的角色，避免账号分页逐条查询。 */
    public Map<Long, List<RoleVO>> loadRoleViews(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserRole> links = userRoleMapper.selectList(Wrappers.<UserRole>lambdaQuery()
                .in(UserRole::getUserId, userIds));
        if (links.isEmpty()) {
            return Map.of();
        }
        Map<Long, Role> rolesById = roleMapper.selectList(Wrappers.<Role>lambdaQuery()
                        .in(Role::getId, links.stream().map(UserRole::getRoleId).distinct().toList()))
                .stream().collect(Collectors.toMap(Role::getId, role -> role));
        Map<Long, List<RoleVO>> result = new HashMap<>();
        for (UserRole link : links) {
            Role role = rolesById.get(link.getRoleId());
            if (role == null) {
                continue;
            }
            result.computeIfAbsent(link.getUserId(), key -> new ArrayList<>()).add(AccountViews.toRoleVO(role));
        }
        result.values().forEach(views -> views.sort(Comparator.comparing(RoleVO::getCode)));
        return result;
    }

    /** 认证过程中临时使用的账号详情；登录完成后 Session 只保留 SessionPrincipal。 */
    public static final class AccountUserDetails implements UserDetails, Serializable {

        private final Long userId;
        private final String username;
        private final String passwordHash;
        private final boolean enabled;
        private final List<GrantedAuthority> authorities;

        AccountUserDetails(Long userId, String username, String passwordHash, boolean enabled,
                           List<GrantedAuthority> authorities) {
            this.userId = userId;
            this.username = username;
            this.passwordHash = passwordHash;
            this.enabled = enabled;
            this.authorities = List.copyOf(authorities);
        }

        public Long getUserId() {
            return this.userId;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return this.authorities;
        }

        @Override
        public String getPassword() {
            return this.passwordHash;
        }

        @Override
        public String getUsername() {
            return this.username;
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return true;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return this.enabled;
        }
    }
}
