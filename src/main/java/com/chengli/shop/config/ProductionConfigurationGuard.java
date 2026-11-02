package com.chengli.shop.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * prod 启动前的配置闸门：缺必填项、互斥 profile 同开或启用功能配置不完整时拒绝启动。
 * 错误只报告配置键名，不输出口令、连接串或其他配置值。
 */
@Slf4j
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionConfigurationGuard implements InitializingBean {

    private static final List<String> FORBIDDEN_PROFILES = List.of("dev", "bootstrap-admin");
    private static final String DEFAULT_CACHE_NAMESPACE = "shop:local";

    private final Environment environment;
    private final ProductCacheProperties productCache;
    private final OrderTimeoutProperties orderTimeout;

    @Override
    public void afterPropertiesSet() {
        List<String> problems = new ArrayList<>();

        for (String profile : environment.getActiveProfiles()) {
            if (FORBIDDEN_PROFILES.contains(profile)) {
                problems.add("spring.profiles.active=" + profile);
            }
        }
        if (Boolean.TRUE.equals(environment.getProperty("shop.bootstrap-admin.enabled", Boolean.class))) {
            problems.add("SHOP_BOOTSTRAP_ADMIN_ENABLED");
        }

        requireEnvironment(problems, "SHOP_DB_URL");
        requireEnvironment(problems, "SHOP_DB_USERNAME");
        requireEnvironment(problems, "SHOP_DB_PASSWORD");

        if (productCache.isEnabled()) {
            requireEnvironment(problems, "SHOP_REDIS_HOST");
            String namespace = productCache.getNamespace();
            if (namespace == null || namespace.isBlank() || DEFAULT_CACHE_NAMESPACE.equals(namespace)) {
                problems.add("SHOP_PRODUCT_CACHE_NAMESPACE");
            }
        }

        if (orderTimeout.isEnabled()) {
            requireEnvironment(problems, "SHOP_RABBITMQ_HOST");
            requireEnvironment(problems, "SHOP_RABBITMQ_VIRTUAL_HOST");
            var rabbit = orderTimeout.getRabbit();
            if (rabbit.getUsername() == null || rabbit.getUsername().isBlank()
                    || "guest".equalsIgnoreCase(rabbit.getUsername())) {
                problems.add("SHOP_RABBITMQ_USERNAME");
            }
            if (rabbit.getPassword() == null || rabbit.getPassword().isBlank()) {
                problems.add("SHOP_RABBITMQ_PASSWORD");
            }
            if (rabbit.getVirtualHost() == null || rabbit.getVirtualHost().isBlank()
                    || "/".equals(rabbit.getVirtualHost())) {
                problems.add("SHOP_RABBITMQ_VIRTUAL_HOST");
            }
        }

        if (!problems.isEmpty()) {
            List<String> sorted = problems.stream().distinct().sorted().toList();
            // 只列出键名/互斥 profile，便于部署时定位，不泄露任何配置值。
            throw new IllegalStateException("生产配置校验失败，请修正配置项: " + String.join(", ", sorted));
        }
        log.info("生产配置校验通过 production profiles=prod productCache={} orderTimeout={}",
                productCache.isEnabled(), orderTimeout.isEnabled());
    }

    private void requireEnvironment(List<String> problems, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            problems.add(key);
        }
    }
}
