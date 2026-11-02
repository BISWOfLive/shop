package com.chengli.shop.identity;

import com.chengli.shop.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 交易身份的当前买家来源：必须是已登录且具有 USER 或 ADMIN 角色。
 * 所有订单查询和写入继续限定此 buyerId，ADMIN 不获得他人订单操作权限。
 */
@Component
public class SecurityCurrentBuyerProvider implements CurrentBuyerProvider {

    private final CurrentUserProvider currentUserProvider;

    public SecurityCurrentBuyerProvider(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public Long currentBuyerId() {
        Long userId = this.currentUserProvider.requireUserId();
        if (!hasAnyBuyerRole()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "当前账号没有买家权限");
        }
        return userId;
    }

    private boolean hasAnyBuyerRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(granted -> {
            String authority = granted.getAuthority();
            return "ROLE_USER".equals(authority) || "ROLE_ADMIN".equals(authority);
        });
    }
}
