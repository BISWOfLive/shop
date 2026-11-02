package com.chengli.notification.identity;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 内部接口的接收人入口：只从通过签名、issuer、audience、时效与 scope 校验的
 * JWT sub 取用户 ID；不读取 Cookie、X-User-Id 或任何请求头身份。
 */
@Component
public class CurrentRecipientProvider {

    public Long requireRecipientId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部凭据无效");
        }
        String subject = jwt.getSubject();
        // 解码器已校验 sub 格式；这里仍做防御性检查，避免未来绕过资源服务器配置。
        if (subject == null || !subject.matches("[1-9][0-9]{0,18}") || Long.parseLong(subject) <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部凭据无效");
        }
        return Long.valueOf(subject);
    }
}
