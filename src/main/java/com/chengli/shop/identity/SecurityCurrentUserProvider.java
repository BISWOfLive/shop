package com.chengli.shop.identity;

import com.chengli.shop.exception.BusinessException;
import com.chengli.shop.security.SessionPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 从已刷新的 SecurityContext 读取用户 ID 与管理角色，供服务层做对象归属和操作人校验。 */
@Component
public class SecurityCurrentUserProvider implements CurrentUserProvider {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";

    @Override
    public Long requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return principal.userId();
    }

    @Override
    public Long requireAdminId() {
        Long userId = requireUserId();
        if (!hasAuthority(ADMIN_ROLE)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
        return userId;
    }

    private boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
