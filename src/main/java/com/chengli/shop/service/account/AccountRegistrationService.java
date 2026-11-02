package com.chengli.shop.service.account;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chengli.shop.dto.UserRegisterRequest;
import com.chengli.shop.entity.Role;
import com.chengli.shop.entity.UserAccount;
import com.chengli.shop.entity.UserRole;
import com.chengli.shop.enums.AccountStatus;
import com.chengli.shop.exception.BusinessException;
import com.chengli.shop.mapper.RoleMapper;
import com.chengli.shop.mapper.UserAccountMapper;
import com.chengli.shop.mapper.UserRoleMapper;
import com.chengli.shop.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 注册只创建启用账号并分配固定 USER 角色，账号与授权在同一事务提交。 */
@Service
@RequiredArgsConstructor
public class AccountRegistrationService {

    private static final String USER_ROLE_CODE = "USER";

    private final UserAccountMapper accountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserVO register(UserRegisterRequest request) {
        String username = AccountViews.normalizeUsername(request.getUsername());
        if (!username.matches("[\\x21-\\x7E]{1,64}")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "用户名仅支持无空格的可见 ASCII 字符");
        }
        String nickname = AccountViews.normalizeNickname(request.getNickname());
        if (nickname.length() > 64) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "昵称不能超过 64 个字符");
        }
        if (accountMapper.selectCount(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getUsername, username)) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "用户名已存在");
        }
        Role userRole = roleMapper.selectOne(Wrappers.<Role>lambdaQuery().eq(Role::getCode, USER_ROLE_CODE));
        if (userRole == null) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "USER 角色未初始化");
        }

        UserAccount account = new UserAccount();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        account.setNickname(nickname);
        account.setStatus(AccountStatus.ENABLED.getCode());
        try {
            if (accountMapper.insert(account) != 1) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "账号写入失败");
            }
        } catch (DuplicateKeyException conflict) {
            throw new BusinessException(HttpStatus.CONFLICT, "用户名已存在");
        }

        UserRole grant = new UserRole();
        grant.setUserId(account.getId());
        grant.setRoleId(userRole.getId());
        if (userRoleMapper.insert(grant) != 1) {
            // 事务回滚，不留下无角色半成品账号。
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "账号授权失败");
        }
        return AccountViews.toUserVO(accountMapper.selectById(account.getId()),
                List.of(AccountViews.toRoleVO(userRole)));
    }
}
