package com.chengli.shop.config;

import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;

/**
 * 通知发布通道的连接、强制路由 + 关联确认模板与独立调度线程。
 * 只由 shop.notification.enabled 控制，不依赖订单到期开关或 profile；
 * 拓扑由通知服务声明，商城发布方不假装绑定一定存在。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "shop.notification", name = "enabled", havingValue = "true")
@EnableScheduling
public class NotificationRabbitConfiguration {
    public static final String EXCHANGE = "shop.notification.exchange";
    public static final String PAID_KEY = "order.paid";
    public static final String CANCELLED_KEY = "order.cancelled";
    public static final String CONNECTION_FACTORY = "notificationConnectionFactory";
    public static final String TEMPLATE = "notificationRabbitTemplate";
    public static final String SCHEDULER = "notificationPublishTaskScheduler";

    @Bean(name = CONNECTION_FACTORY)
    public CachingConnectionFactory notificationConnectionFactory(NotificationProperties properties) {
        var settings = properties.getRabbit();
        Assert.hasText(settings.getUsername(), "启用通知模块需配置 SHOP_RABBITMQ_USERNAME");
        var factory = new CachingConnectionFactory(settings.getHost(), settings.getPort());
        factory.setVirtualHost(settings.getVirtualHost());
        factory.setUsername(settings.getUsername());
        factory.setPassword(settings.getPassword());
        factory.setConnectionTimeout(settings.getConnectionTimeoutMillis());
        factory.getRabbitConnectionFactory().setHandshakeTimeout(settings.getConnectionTimeoutMillis());
        factory.getRabbitConnectionFactory().setChannelRpcTimeout(settings.getConnectionTimeoutMillis());
        factory.setRequestedHeartBeat(30);
        // 必须同时拿到 correlated confirm ack 且没有 mandatory return 才能标记发布成功。
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        return factory;
    }

    @Bean(name = TEMPLATE)
    public RabbitTemplate notificationRabbitTemplate(
            @org.springframework.beans.factory.annotation.Qualifier(CONNECTION_FACTORY)
            CachingConnectionFactory notificationConnectionFactory) {
        var template = new RabbitTemplate(notificationConnectionFactory);
        template.setMandatory(true);
        // 持久投递由每条消息的 deliveryMode 设置；模板只保证强制路由 + 关联确认。
        return template;
    }

    @Bean(name = SCHEDULER)
    public ThreadPoolTaskScheduler notificationPublishTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        // 独立于到期扫描/发布线程，逐条等待 confirm 时不影响原超时通道。
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("notification-publish-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
