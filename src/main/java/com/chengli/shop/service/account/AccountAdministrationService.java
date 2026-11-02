package com.chengli.shop.service.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengli.shop.common.PageResult;
import com.chengli.shop.dto.UserQuery;
import com.chengli.shop.dto.UserRoleSaveRequest;
import com.chengli.shop.dto.UserStatusUpdateRequest;
import com.chengli.shop.entity.Role;
import com.chengli.shop.entity.UserAccount;
import com.chengli.shop.entity.UserRole;
import com.chengli.shop.enums.AccountStatus;
import com.chengli.shop.exception.BusinessException;
import com.chengli.shop.mapper.RoleMapper;
import com.chengli.shop.mapper.UserAccountMapper;
import com.chengli.shop.mapper.UserRoleMapper;
import com.chengli.shop.security.AccountUserDetailsService;
import com.chengli.shop.vo.RoleVO;
import com.chengli.shop.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员账号维护。状态与角色变更使用固定锁顺序：先锁 ADMIN 角色行，再锁目标账号，
 * 在事务内保证变更后至少保留一名启用管理员。角色码固定为 ADMIN/USER，不允许编辑或新增。
 */
@Service
@RequiredArgsConstructor
public class AccountAdministrationService {

    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final Set<String> FIXED_ROLE_CODES = Set.of("ADMIN", "USER");

    private final UserAccountMapper accountMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final AccountUserDetailsService accounts;

    @Transactional(readOnly = true)
    public PageResult<UserVO> page(UserQuery query) {
        LambdaQueryWrapper<UserAccount> wrapper = Wrappers.lambdaQuery();
        String keyword = query.getKeyword() == null ? "" : query.getKeyword().strip();
        if (!keyword.isEmpty()) {
            // 关键词只在用户名/昵称内匹配；LIKE 通配符按字面处理。
            String escaped = keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
            wrapper.apply("(username LIKE {0} ESCAPE '!' OR nickname LIKE {0} ESCAPE '!')", "%" + escaped + "%");
        }
        wrapper.eq(query.getStatus() != null, UserAccount::getStatus, query.getStatus())
                .orderByDesc(UserAccount::getCreatedAt, UserAccount::getId);

        Page<UserAccount> page = new Page<>(query.getPage(), query.getSize());
        this.accountMapper.selectPage(page, wrapper);
        List<Long> userIds = page.getRecords().stream().map(UserAccount::getId).toList();
        Map<Long, List<RoleVO>> roles = this.accounts.loadRoleViews(userIds);
        List<UserVO> records = page.getRecords().stream()
                .map(account -> AccountViews.toUserVO(account, roles.getOrDefault(account.getId(), List.of())))
                .toList();
        return new PageResult<>(records, page.getTotal(), query.getPage(), query.getSize());
    }

    @Transactional
    public UserVO changeStatus(Long userId, UserStatusUpdateRequest request) {
        Role adminRole = lockAdminRole();
        UserAccount target = lockAccount(userId);
        int nextStatus = request.getStatus();
        if (nextStatus == AccountStatus.ENABLED.getCode() && !hasUsablePassword(target)) {
            // 数据库 CHECK 也会拒绝，但这里先给出明确的业务冲突，不生成口令。
            throw new BusinessException(HttpStatus.CONFLICT, "账号缺少有效密码，不能启用");
        }
        boolean removesEnabledAdmin = Integer.valueOf(AccountStatus.ENABLED.getCode()).equals(target.getStatus())
                && nextStatus == AccountStatus.DISABLED.getCode()
                && hasRole(userId, adminRole.getId());
        if (removesEnabledAdmin && countEnabledAdmins(adminRole.getId()) <= 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "至少保留一名启用管理员");
        }
        int updated = this.accountMapper.update(null, Wrappers.<UserAccount>lambdaUpdate()
                .eq(UserAccount::getId, userId)
                .set(UserAccount::getStatus, nextStatus)
                .setSql("updated_at = CURRENT_TIMESTAMP(3)"));
        if (updated != 1) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return userVO(userId);
    }

    @Transactional
    public UserVO replaceRoles(Long userId, UserRoleSaveRequest request) {
        if (!Objects.equals(userId, request.getUserId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "路径用户 ID 与请求体不一致");
        }
        Role adminRole = lockAdminRole();
        UserAccount target = lockAccount(userId);
        Set<Long> roleIds = request.getRoleIds();
        List<Role> roles = roleIds.isEmpty() ? List.of()
                : this.roleMapper.selectList(Wrappers.<Role>lambdaQuery()
                        .in(Role::getId, roleIds).orderByAsc(Role::getId));
        if (roles.size() != roleIds.size()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "角色不存在");
        }
        for (Role role : roles) {
            if (role.getCode() == null || !FIXED_ROLE_CODES.contains(role.getCode())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "角色码不可编辑");
            }
        }
        boolean removesEnabledAdmin = Integer.valueOf(AccountStatus.ENABLED.getCode()).equals(target.getStatus())
                && hasRole(userId, adminRole.getId())
                && !roleIds.contains(adminRole.getId());
        if (removesEnabledAdmin && countEnabledAdmins(adminRole.getId()) <= 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "至少保留一名启用管理员");
        }

        this.userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, userId));
        for (Role role : roles) {
            UserRole grant = new UserRole();
            grant.setUserId(userId);
            grant.setRoleId(role.getId());
            if (this.userRoleMapper.insert(grant) != 1) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "角色授权写入失败");
            }
        }
        return userVO(userId);
    }

    private Role lockAdminRole() {
        Role adminRole = this.roleMapper.selectOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getCode, ADMIN_ROLE_CODE).last("FOR UPDATE"));
        if (adminRole == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "ADMIN 角色未初始化");
        }
        return adminRole;
    }

    private UserAccount lockAccount(Long userId) {
        UserAccount account = this.accountMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getId, userId).last("FOR UPDATE"));
        if (account == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return account;
    }

    private UserVO userVO(Long userId) {
        UserAccount account = this.accountMapper.selectById(userId);
        if (account == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        return AccountViews.toUserVO(account, this.accounts.loadRoleViews(userId));
    }

    private boolean hasRole(Long userId, Long roleId) {
        return this.userRoleMapper.selectCount(Wrappers.<UserRole>lambdaQuery()
                .eq(UserRole::getUserId, userId).eq(UserRole::getRoleId, roleId)) > 0;
    }

    private boolean hasUsablePassword(UserAccount account) {
        return account.getPasswordHash() != null && !account.getPasswordHash().isBlank();
    }

    private long countEnabledAdmins(Long adminRoleId) {
        List<Long> userIds = this.userRoleMapper.selectList(Wrappers.<UserRole>lambdaQuery()
                        .select(UserRole::getUserId)
                        .eq(UserRole::getRoleId, adminRoleId))
                .stream().map(UserRole::getUserId).collect(Collectors.toSet())
                .stream().toList();
        if (userIds.isEmpty()) {
            return 0;
        }
        return this.accountMapper.selectCount(Wrappers.<UserAccount>lambdaQuery()
                .in(UserAccount::getId, userIds)
                .eq(UserAccount::getStatus, AccountStatus.ENABLED.getCode()));
    }
}
