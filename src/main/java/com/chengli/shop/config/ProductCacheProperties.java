package com.chengli.shop.config;

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
@ConfigurationProperties(prefix = "shop.product-cache")
public class ProductCacheProperties {
    private boolean enabled = false;
    @NotBlank
    private String namespace = "shop:local";
    @Positive
    private int ttlSeconds = 300;
    @Min(0)
    private int jitterSeconds = 60;
    @Positive
    private int nullTtlSeconds = 30;
}
