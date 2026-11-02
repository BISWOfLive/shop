package com.chengli.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

/**
 * 通知工作/失败拓扑与手动 ACK 监听容器。仅由 notification.consumer.enabled 控制装配；
 * 工作队列失败出口为失败交换机，禁止无限 requeue，也不设置 30 分钟 TTL。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "notification.consumer", name = "enabled", havingValue = "true")
@EnableRabbit
public class NotificationRabbitConfiguration {
    public static final String EXCHANGE = "shop.notification.exchange";
    public static final String WORK_QUEUE = "shop.notification.queue";
    public static final String PAID_KEY = "order.paid";
    public static final String CANCELLED_KEY = "order.cancelled";
    public static final String FAILURE_EXCHANGE = "shop.notification.failure.exchange";
    public static final String FAILED_QUEUE = "shop.notification.failed.queue";
    public static final String FAILED_KEY = "notification.failed";
    public static final String CONNECTION_FACTORY = "notificationConsumerConnectionFactory";
    public static final String LISTENER_FACTORY = "notificationListenerContainerFactory";

    @Bean(name = CONNECTION_FACTORY)
    public CachingConnectionFactory notificationConsumerConnectionFactory(NotificationConsumerProperties properties) {
        var settings = properties.getRabbit();
        Assert.hasText(settings.getUsername(), "启用通知消费需配置 NOTIFICATION_RABBITMQ_USERNAME");
        var factory = new CachingConnectionFactory(settings.getHost(), settings.getPort());
        factory.setVirtualHost(settings.getVirtualHost());
        factory.setUsername(settings.getUsername());
        factory.setPassword(settings.getPassword());
        factory.setConnectionTimeout(settings.getConnectionTimeoutMillis());
        factory.getRabbitConnectionFactory().setHandshakeTimeout(settings.getConnectionTimeoutMillis());
        factory.getRabbitConnectionFactory().setChannelRpcTimeout(settings.getConnectionTimeoutMillis());
        factory.setRequestedHeartBeat(30);
        return factory;
    }

    @Bean
    public RabbitAdmin notificationRabbitAdmin(
            @Qualifier(CONNECTION_FACTORY) CachingConnectionFactory notificationConsumerConnectionFactory) {
        return new RabbitAdmin(notificationConsumerConnectionFactory);
    }

    @Bean
    public Declarables notificationTopology() {
        var exchange = new DirectExchange(EXCHANGE, true, false);
        var failureExchange = new DirectExchange(FAILURE_EXCHANGE, true, false);
        var work = QueueBuilder.durable(WORK_QUEUE)
                .deadLetterExchange(FAILURE_EXCHANGE)
                .deadLetterRoutingKey(FAILED_KEY)
                .build();
        var failed = QueueBuilder.durable(FAILED_QUEUE).build();
        return new Declarables(exchange, failureExchange, work, failed,
                BindingBuilder.bind(work).to(exchange).with(PAID_KEY),
                BindingBuilder.bind(work).to(exchange).with(CANCELLED_KEY),
                BindingBuilder.bind(failed).to(failureExchange).with(FAILED_KEY));
    }

    @Bean(name = LISTENER_FACTORY)
    public SimpleRabbitListenerContainerFactory notificationListenerContainerFactory(
            @Qualifier(CONNECTION_FACTORY) CachingConnectionFactory notificationConsumerConnectionFactory,
            NotificationConsumerProperties properties) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(notificationConsumerConnectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(properties.getConcurrency());
        factory.setMaxConcurrentConsumers(properties.getConcurrency());
        factory.setPrefetchCount(properties.getPrefetch());
        // 失败消息拒绝后进入失败队列，不无限回灌工作队列。
        factory.setDefaultRequeueRejected(false);
        factory.setMissingQueuesFatal(false);
        factory.setRecoveryInterval(5000L);
        return factory;
    }
}
