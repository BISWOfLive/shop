package com.chengli.shop.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
@Profile({"dev", "prod"})
@ConditionalOnProperty(prefix = "shop.order-timeout", name = "enabled", havingValue = "true")
@EnableRabbit
@EnableScheduling
public class OrderRabbitConfiguration {
    public static final String DELAY_EXCHANGE = "shop.order.delay.exchange";
    public static final String DELAY_QUEUE = "shop.order.delay.30m.queue";
    public static final String DELAY_KEY = "order.created.delay";
    public static final String TIMEOUT_EXCHANGE = "shop.order.timeout.exchange";
    public static final String TIMEOUT_QUEUE = "shop.order.timeout.queue";
    public static final String TIMEOUT_KEY = "timeout.cancel";
    public static final String FAILURE_EXCHANGE = "shop.order.failure.exchange";
    public static final String FAILURE_QUEUE = "shop.order.timeout.failed.queue";
    public static final String FAILURE_KEY = "timeout.failed";
    public static final String CONSUMER_NAME = "order-timeout-cancel-v1";

    @Bean
    public CachingConnectionFactory orderTimeoutConnectionFactory(OrderTimeoutProperties properties) {
        var settings = properties.getRabbit();
        Assert.hasText(settings.getUsername(), "启用订单超时模块需配置 SHOP_RABBITMQ_USERNAME");
        var factory = new CachingConnectionFactory(settings.getHost(), settings.getPort());
        factory.setVirtualHost(settings.getVirtualHost());
        factory.setUsername(settings.getUsername());
        factory.setPassword(settings.getPassword());
        factory.setConnectionTimeout(settings.getConnectionTimeoutMillis());
        factory.getRabbitConnectionFactory().setHandshakeTimeout(settings.getConnectionTimeoutMillis());
        factory.getRabbitConnectionFactory().setChannelRpcTimeout(settings.getConnectionTimeoutMillis());
        factory.setRequestedHeartBeat(30);
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        return factory;
    }

    @Bean
    public RabbitTemplate orderTimeoutRabbitTemplate(CachingConnectionFactory orderTimeoutConnectionFactory) {
        var template = new RabbitTemplate(orderTimeoutConnectionFactory);
        template.setMandatory(true);
        return template;
    }

    @Bean
    public RabbitAdmin orderTimeoutRabbitAdmin(CachingConnectionFactory orderTimeoutConnectionFactory) {
        var admin = new RabbitAdmin(orderTimeoutConnectionFactory);
        // MQ 暂时不可用或声明失败不能阻止独立数据库扫描启动；错误仍由 AMQP 日志保留。
        admin.setIgnoreDeclarationExceptions(true);
        return admin;
    }

    @Bean
    public Declarables orderTimeoutTopology() {
        var delay = new DirectExchange(DELAY_EXCHANGE, true, false);
        var timeout = new DirectExchange(TIMEOUT_EXCHANGE, true, false);
        var failure = new DirectExchange(FAILURE_EXCHANGE, true, false);
        var waiting = QueueBuilder.durable(DELAY_QUEUE).ttl(1800000)
                .deadLetterExchange(TIMEOUT_EXCHANGE).deadLetterRoutingKey(TIMEOUT_KEY).build();
        var work = QueueBuilder.durable(TIMEOUT_QUEUE)
                .deadLetterExchange(FAILURE_EXCHANGE).deadLetterRoutingKey(FAILURE_KEY).build();
        var failed = QueueBuilder.durable(FAILURE_QUEUE).build();
        return new Declarables(delay, timeout, failure, waiting, work, failed,
                BindingBuilder.bind(waiting).to(delay).with(DELAY_KEY),
                BindingBuilder.bind(work).to(timeout).with(TIMEOUT_KEY),
                BindingBuilder.bind(failed).to(failure).with(FAILURE_KEY));
    }

    @Bean
    public SimpleRabbitListenerContainerFactory orderTimeoutListenerContainerFactory(
            CachingConnectionFactory orderTimeoutConnectionFactory, OrderTimeoutProperties properties) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(orderTimeoutConnectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(properties.getConcurrency());
        factory.setMaxConcurrentConsumers(properties.getConcurrency());
        factory.setPrefetchCount(properties.getPrefetch());
        factory.setDefaultRequeueRejected(false);
        factory.setMissingQueuesFatal(false);
        factory.setRecoveryInterval(5000L);
        return factory;
    }

    @Bean
    public ThreadPoolTaskScheduler orderTimeoutTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        // 发布可能逐条等待 confirm，第二个线程保留给独立到期扫描。
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("order-timeout-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        return scheduler;
    }
}
