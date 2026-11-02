package com.chengli.shop.service.messaging;

import com.chengli.shop.config.NotificationProperties;
import com.chengli.shop.config.NotificationRabbitConfiguration;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 终态通知消息发布：按 eventType 选择 order.paid / order.cancelled 路由。
 * 持久消息、业务 messageId=eventId、传输相关号每次唯一；只有同时收到 confirm ack 且没有
 * mandatory return 才返回成功，方法本身返回或队列存在都不等于消费者已处理。
 */
@Service
@ConditionalOnProperty(prefix = "shop.notification", name = "enabled", havingValue = "true")
public class NotificationMessagePublisher {
    private static final String PAID_KEY = NotificationRabbitConfiguration.PAID_KEY;
    private static final String CANCELLED_KEY = NotificationRabbitConfiguration.CANCELLED_KEY;

    private final RabbitTemplate template;
    private final NotificationProperties properties;
    private final OrderNotificationEventCodec codec;

    public NotificationMessagePublisher(
            @Qualifier(NotificationRabbitConfiguration.TEMPLATE) RabbitTemplate template,
            NotificationProperties properties, OrderNotificationEventCodec codec) {
        this.template = template;
        this.properties = properties;
        this.codec = codec;
    }

    @Transactional(propagation = Propagation.NEVER)
    public PublishOutcome publish(OrderNotificationEvent event) {
        try {
            var messageProperties = new MessageProperties();
            messageProperties.setMessageId(event.eventId());
            messageProperties.setType(event.eventType());
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
            messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            var message = new Message(codec.encode(event).getBytes(StandardCharsets.UTF_8), messageProperties);
            // 传输相关号每次唯一；业务 eventId 与 messageId 在租约重投时保持不变。
            var correlation = new CorrelationData(event.eventId() + ":" + UUID.randomUUID());
            template.send(NotificationRabbitConfiguration.EXCHANGE, routingKey(event), message, correlation);
            var confirm = correlation.getFuture().get(properties.getConfirmTimeoutMillis(), TimeUnit.MILLISECONDS);
            if (correlation.getReturned() != null) {
                return PublishOutcome.failed("mandatory return: " + correlation.getReturned().getReplyText());
            }
            if (!confirm.ack()) return PublishOutcome.failed("broker NACK: " + confirm.reason());
            return PublishOutcome.confirmed();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return PublishOutcome.failed("发布确认等待被中断");
        } catch (Exception exception) {
            return PublishOutcome.failed(exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private String routingKey(OrderNotificationEvent event) {
        if ("ORDER_PAID".equals(event.eventType())) return PAID_KEY;
        if ("ORDER_CANCELLED".equals(event.eventType())) return CANCELLED_KEY;
        throw new IllegalArgumentException("通知发布只接受 ORDER_PAID / ORDER_CANCELLED");
    }
}
