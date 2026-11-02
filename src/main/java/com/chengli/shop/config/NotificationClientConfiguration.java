package com.chengli.shop.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 商城到通知服务的内部 HTTP 客户端：连接 500ms、单次响应 2s，不自动重放写操作。
 * 只是客户端模板，启动时不建立网络连接；通知服务不可用不进入下单/支付/取消调用链。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "shop.notification", name = "enabled", havingValue = "true")
public class NotificationClientConfiguration {

    @Bean
    public RestClient notificationRestClient(NotificationProperties properties) {
        var internal = properties.getInternal();
        // 只有通知功能开启时才要求内部地址；关闭时不装配本配置，也不因空地址阻止启动。
        org.springframework.util.Assert.hasText(internal.getBaseUrl(),
                "启用通知需配置 SHOP_NOTIFICATION_INTERNAL_BASE_URL");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(internal.getConnectTimeoutMillis());
        factory.setReadTimeout(internal.getResponseTimeoutMillis());
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(internal.getBaseUrl())
                .build();
    }
}
