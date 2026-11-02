package com.chengli.notification.config;

import com.chengli.notification.common.ApiResponse;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * 内部资源服务安全：无 Session、无 Cookie、无 CSRF；只接受经 kid 白名单、RS256 签名、
 * issuer、audience、sub 与时效校验的 Bearer JWT，scope 决定读/写能力。
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class NotificationSecurityConfiguration {
    public static final String SCOPE_READ = "SCOPE_notifications:read";
    public static final String SCOPE_WRITE = "SCOPE_notifications:write";

    @Bean
    public SecurityFilterChain notificationSecurityFilterChain(HttpSecurity http,
                                                               JwtDecoder notificationJwtDecoder,
                                                               JsonMapper jsonMapper) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, exception) ->
                writeError(response, jsonMapper, HttpStatus.UNAUTHORIZED, "内部凭据无效");
        AccessDeniedHandler deniedHandler = (request, response, exception) ->
                writeError(response, jsonMapper, HttpStatus.FORBIDDEN, "没有通知访问权限");
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(notificationJwtDecoder))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/internal/notifications",
                                "/internal/notifications/unread-count").hasAuthority(SCOPE_READ)
                        .requestMatchers(HttpMethod.POST, "/internal/notifications/*/read")
                        .hasAuthority(SCOPE_WRITE)
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    public JwtDecoder notificationJwtDecoder(NotificationSecurityProperties properties) {
        return buildJwtDecoder(properties);
    }

    /** 构建只接受配置 pubkey 与 RS256 的 JWT 解码器；测试直接复用同一实现。 */
    public static JwtDecoder buildJwtDecoder(NotificationSecurityProperties properties) {
        if (properties.getKeys() == null || properties.getKeys().isEmpty()) {
            throw new IllegalStateException("通知验签公钥未配置");
        }
        List<JWK> jwks = properties.getKeys().entrySet().stream().map(entry -> (JWK) new RSAKey.Builder(
                        parsePublicKey(entry.getValue()))
                        .keyID(entry.getKey())
                        .build())
                .toList();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwks));
        var processor = new DefaultJWTProcessor<SecurityContext>();
        // 仅 RS256：其他算法、无签名 token 和未知 kid 在验签选择阶段即失败。
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        var decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(Duration.ofSeconds(properties.getClockSkewSeconds())),
                new JwtIssuerValidator(properties.getIssuer()),
                audienceValidator(properties.getAudience()),
                identityValidator())));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return token -> token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : failure("内部凭据 audience 无效");
    }

    private static OAuth2TokenValidator<Jwt> identityValidator() {
        return token -> {
            String subject = token.getSubject();
            // sub 必须原本就是正十进制字符串；时效要求 exp/iat 都存在，不接受无时效凭据。
            if (subject == null || !subject.matches("[1-9][0-9]{0,18}")
                    || token.getIssuedAt() == null || token.getExpiresAt() == null) {
                return failure("内部凭据主体或时效无效");
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }

    private static RSAPublicKey parsePublicKey(String pem) {
        try {
            String body = pem == null ? "" : pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(body);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (RuntimeException | java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("通知验签公钥格式无效");
        }
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse response, JsonMapper jsonMapper,
                                   HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(jsonMapper.writeValueAsBytes(
                ApiResponse.error(status.value(), message)));
    }
}
