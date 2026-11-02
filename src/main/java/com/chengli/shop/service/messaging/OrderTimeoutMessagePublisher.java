package com.chengli.shop.service.messaging;

import com.chengli.shop.config.OrderRabbitConfiguration;
import com.chengli.shop.config.OrderTimeoutProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Profile({"dev", "prod"})
@ConditionalOnProperty(prefix = "shop.order-timeout", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class OrderTimeoutMessagePublisher {
    private final RabbitTemplate orderTimeoutRabbitTemplate;
    private final OrderTimeoutProperties properties;
    private final OrderEventCodec codec;

    @Transactional(propagation = Propagation.NEVER)
    public PublishOutcome publish(OrderCreatedEvent event) {
        try {
            var messageProperties = new MessageProperties();
            messageProperties.setMessageId(event.eventId());
            messageProperties.setType(event.eventType());
            messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            messageProperties.setContentEncoding(StandardCharsets.UTF_8.name());
            messageProperties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            var message = new Message(codec.encode(event).getBytes(StandardCharsets.UTF_8), messageProperties);
            // 传输相关号每次唯一；业务 eventId 和 messageId 在租约重投时保持不变。
            var correlation = new CorrelationData(event.eventId() + ":" + UUID.randomUUID());
            orderTimeoutRabbitTemplate.send(OrderRabbitConfiguration.DELAY_EXCHANGE,
                    OrderRabbitConfiguration.DELAY_KEY, message, correlation);
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
}
