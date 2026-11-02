package com.chengli.shop.service.account;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chengli.shop.dto.UserLoginRequest;
import com.chengli.shop.dto.UserProfileUpdateRequest;
import com.chengli.shop.config.NotificationProperties;
import com.chengli.shop.entity.UserAccount;
import com.chengli.shop.exception.BusinessException;
import com.chengli.shop.mapper.UserAccountMapper;
import com.chengli.shop.security.AccountUserDetailsService;
import com.chengli.shop.security.SessionPrincipal;
import com.chengli.shop.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationException;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 自定义 JSON 登录：认证后执行会话固定防护和 CSRF 轮换，并显式保存 SecurityContext。
 */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final AccountUserDetailsService accounts;
    private final UserAccountMapper accountMapper;
    private final NotificationProperties notificationProperties;

    @Value("${server.servlet.session.cookie.name:SHOPSESSION}")
    private String sessionCookieName;

    @Value("${server.servlet.session.cookie.secure:false}")
    private boolean sessionCookieSecure;

    @Value("${server.servlet.session.cookie.same-site:lax}")
    private String sessionCookieSameSite;

    public UserVO login(UserLoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication;
        try {
            authentication = this.authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    AccountViews.normalizeUsername(request.getUsername()), request.getPassword()));
        } catch (AuthenticationException authenticationFailure) {
            // 账号不存在、禁用、无口令与密码错误统一返回同一提示。
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!(authentication.getPrincipal() instanceof AccountUserDetailsService.AccountUserDetails details)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        SessionPrincipal principal = new SessionPrincipal(details.getUserId());
        Authentication sessionAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, details.getAuthorities());
        try {
            this.sessionAuthenticationStrategy.onAuthentication(sessionAuthentication, httpRequest, httpResponse);
        } catch (SessionAuthenticationException sessionFailure) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录状态建立失败，请重试");
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(sessionAuthentication);
        SecurityContextHolder.setContext(context);
        this.securityContextRepository.saveContext(context, httpRequest, httpResponse);
        return userVO(details.getUserId(), HttpStatus.UNAUTHORIZED);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        // 退出后清 Cookie；会话已失效，旧 Cookie 不再获得授权。
        ResponseCookie cookie = ResponseCookie.from(this.sessionCookieName, "")
                .path("/")
                .maxAge(Duration.ZERO)
                .httpOnly(true)
                .secure(this.sessionCookieSecure)
                .sameSite(this.sessionCookieSameSite)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    @Transactional(readOnly = true)
    public UserVO currentUser() {
        return userVO(requireUserId(), HttpStatus.UNAUTHORIZED);
    }

    @Transactional
    public UserVO updateProfile(UserProfileUpdateRequest request) {
        Long userId = requireUserId();
        String nickname = AccountViews.normalizeNickname(request.getNickname());
        if (nickname.length() > 64) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "昵称不能超过 64 个字符");
        }
        int updated = this.accountMapper.update(null, Wrappers.<UserAccount>lambdaUpdate()
                .eq(UserAccount::getId, userId)
                .set(UserAccount::getNickname, nickname)
                .setSql("updated_at = CURRENT_TIMESTAMP(3)"));
        if (updated != 1) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        return userVO(userId, HttpStatus.UNAUTHORIZED);
    }

    private Long requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return principal.userId();
    }

    private UserVO userVO(Long userId, HttpStatus missingStatus) {
        UserAccount account = this.accountMapper.selectById(userId);
        if (account == null) {
            throw new BusinessException(missingStatus,
                    missingStatus == HttpStatus.UNAUTHORIZED ? "登录状态已失效，请重新登录" : "用户不存在");
        }
        return AccountViews.toUserVO(account, this.accounts.loadRoleViews(userId),
                this.notificationProperties.isEnabled());
    }
}
