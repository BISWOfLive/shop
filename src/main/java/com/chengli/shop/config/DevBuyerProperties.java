package com.chengli.shop.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "shop.dev")
public class DevBuyerProperties {
    private Long buyerId;
}
