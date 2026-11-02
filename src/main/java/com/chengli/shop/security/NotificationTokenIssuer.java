package com.chengli.shop.security;

import com.chengli.shop.config.NotificationProperties;
import com.chengli.shop.exception.BusinessException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * 商城签发的内部 RS256 凭据：issuer/audience/algorithm/有效期固定，sub 只取真实用户 ID，
 * scope 由调用方在固定常量中选择。token 不返回浏览器、不放 URL、不写日志；
 * 私钥缺失或 key id 未配置时明确返回 503，不退回无签名身份头。
 */
@Service
public class NotificationTokenIssuer {
    public static final String SCOPE_READ = "notifications:read";
    public static final String SCOPE_WRITE = "notifications:write";

    private final NotificationProperties properties;
    private volatile RSAPrivateKey signingKey;
    private volatile String loadedPem;

    public NotificationTokenIssuer(NotificationProperties properties) {
        this.properties = properties;
    }

    public String issue(Long userId, String scope) {
        if (!properties.isEnabled()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "通知模块不可用");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("通知凭据主体无效");
        }
        if (!SCOPE_READ.equals(scope) && !SCOPE_WRITE.equals(scope)) {
            throw new IllegalArgumentException("通知凭据 scope 无效");
        }
        var token = properties.getToken();
        if (token.getKid() == null || token.getKid().isBlank()
                || token.getPrivateKey() == null || token.getPrivateKey().isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "通知签名密钥未配置");
        }
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .issuer(token.getIssuer())
                .audience(token.getAudience())
                .subject(userId.toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(token.getTtlSeconds())))
                .claim("scope", scope)
                .build();
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(token.getKid()).build();
        try {
            var jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(signingKey(token.getPrivateKey())));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "通知凭据签发失败");
        }
    }

    private RSAPrivateKey signingKey(String pem) {
        // 私钥只在配置变化时重新解析，避免每次请求重复解码。
        if (signingKey != null && pem.equals(loadedPem)) {
            return signingKey;
        }
        synchronized (this) {
            if (signingKey != null && pem.equals(loadedPem)) {
                return signingKey;
            }
            try {
                String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
                byte[] der = Base64.getDecoder().decode(body);
                RSAPrivateKey key = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                        .generatePrivate(new PKCS8EncodedKeySpec(der));
                this.signingKey = key;
                this.loadedPem = pem;
                return key;
            } catch (RuntimeException | java.security.GeneralSecurityException exception) {
                // 错误信息不带私钥内容，只说明格式不受支持。
                throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "通知签名私钥格式无效");
            }
        }
    }
}
