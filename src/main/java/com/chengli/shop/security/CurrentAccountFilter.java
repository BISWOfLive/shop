package com.chengli.shop.security;

import com.chengli.shop.entity.UserAccount;
import com.chengli.shop.enums.AccountStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 在授权判定前按 Session 中的用户 ID 重新读取启用状态和角色。
 * 账号禁用/删除立即失效会话；数据库故障返回 503，不回退旧角色或开发买家。
 * 该过滤器只加入 Spring Security 链一次，不作为独立 Servlet Filter 注册。
 */
public class CurrentAccountFilter extends OncePerRequestFilter {

    private final AccountUserDetailsService accounts;
    private final JsonSecurityErrorHandler errors;

    public CurrentAccountFilter(AccountUserDetailsService accounts, JsonSecurityErrorHandler errors) {
        this.accounts = accounts;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            UserAccount account = this.accounts.loadAccount(principal.userId());
            if (account == null || !Integer.valueOf(AccountStatus.ENABLED.getCode()).equals(account.getStatus())) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
                this.errors.write(response, HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录");
                return;
            }
            List<GrantedAuthority> authorities = this.accounts.loadAuthorities(account.getId());
            Authentication refreshed = UsernamePasswordAuthenticationToken.authenticated(
                    principal, null, authorities);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(refreshed);
            SecurityContextHolder.setContext(context);
            filterChain.doFilter(request, response);
        } catch (DataAccessException | PersistenceException databaseFailure) {
            SecurityContextHolder.clearContext();
            this.errors.write(response, HttpStatus.SERVICE_UNAVAILABLE, "身份校验暂时不可用，请稍后重试");
        }
    }
}
