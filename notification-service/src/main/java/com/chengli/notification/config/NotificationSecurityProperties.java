package com.chengli.notification.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内部凭据验证配置：通知服务只持公钥，按 kid 白名单选择。
 * 不配置任何共享口令或可回退的请求头身份。
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "notification.security")
public class NotificationSecurityProperties {
    @NotBlank
    private String issuer = "shop";
    @NotBlank
    private String audience = "shop-notification";
    /** 允许的时钟偏差最多 5 秒，配置校验也限制在 0～60 秒。 */
    @Min(0)
    @Max(60)
    private int clockSkewSeconds = 5;
    /** kid -> X.509 公钥 PEM；未知 kid 在验签阶段直接拒绝。 */
    @NotEmpty
    private Map<String, String> keys = new LinkedHashMap<>();
}
