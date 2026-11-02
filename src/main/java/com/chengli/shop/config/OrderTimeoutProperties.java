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

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "shop.order-timeout")
public class OrderTimeoutProperties {
    private boolean enabled = false;
    @Positive
    private long publishPollMillis = 1000;
    @Positive
    private long scanPollMillis = 30000;
    @Min(30) @Max(3600)
    private int leaseSeconds = 30;
    @Min(1) @Max(5000)
    private int confirmTimeoutMillis = 5000;
    @Min(1) @Max(32)
    private int concurrency = 2;
    @Min(1) @Max(100)
    private int prefetch = 10;
    @Valid
    private Rabbit rabbit = new Rabbit();

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
}
