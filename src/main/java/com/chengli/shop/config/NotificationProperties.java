package com.chengli.shop.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 站内通知开关、独立发布通道、内部调用与签名凭据参数。
 * 关闭时只保留数据与能力定义，不装配任何 MQ 连接、发布任务或通知入口。
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "shop.notification")
public class NotificationProperties {
    private boolean enabled = false;
    @Positive
    private long publishPollMillis = 1000;
    @Min(30) @Max(3600)
    private int leaseSeconds = 30;
    @Min(1) @Max(5000)
    private int confirmTimeoutMillis = 5000;
    @Valid
    private Rabbit rabbit = new Rabbit();
    @Valid
    private Internal internal = new Internal();
    @Valid
    private Token token = new Token();

    @Data
    public static class Rabbit {
        @NotBlank
        private String host = "127.0.0.1";
        @Min(1) @Max(65535)
        private int port = 5672;
        @NotBlank
        private String virtualHost = "/";
        private String username = "";
        private String password = "";
        @Min(1) @Max(10000)
        private int connectionTimeoutMillis = 5000;
    }

    /** 商城到通知服务的内部 HTTP 调用边界。 */
    @Data
    public static class Internal {
        /**
         * 默认值用于本地开发；prod 允许关闭通知时为空，启用时才要求非空。
         * 不在属性绑定阶段强制非空，避免“功能关闭但启动失败”。
         */
        private String baseUrl = "http://127.0.0.1:8082";
        @Min(1) @Max(60000)
        private int connectTimeoutMillis = 500;
        @Min(1) @Max(60000)
        private int responseTimeoutMillis = 2000;
        @Min(1024) @Max(1048576)
        private int maxResponseBytes = 65536;
    }

    /** 商城持有私钥、通知服务只持公钥的内部 RS256 凭据。 */
    @Data
    public static class Token {
        /** 私钥 PKCS#8 PEM，只从部署环境注入，不写入仓库。 */
        private String privateKey = "";
        /** 当前签名公钥标识；通知服务按该 kid 选择允许公钥。 */
        private String kid = "";
        @NotBlank
        private String issuer = "shop";
        @NotBlank
        private String audience = "shop-notification";
        @Min(5) @Max(300)
        private int ttlSeconds = 30;
        @Min(0) @Max(60)
        private int clockSkewSeconds = 5;
    }
}
