package com.chengli.shop.service.messaging;

import com.chengli.shop.config.OrderRabbitConfiguration;
import com.chengli.shop.observability.ShopBusinessMetrics;
import com.chengli.shop.service.transaction.TimeoutMessageTransactionService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

@Slf4j
@Component
@Profile({"dev", "prod"})
@ConditionalOnProperty(prefix = "shop.order-timeout", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class OrderTimeoutListener {
    private final OrderEventCodec codec;
    private final TimeoutMessageTransactionService transactions;
    private final ShopBusinessMetrics metrics;

    @RabbitListener(queues = OrderRabbitConfiguration.TIMEOUT_QUEUE,
            containerFactory = "orderTimeoutListenerContainerFactory")
    public void receive(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        OrderCreatedEvent event;
        try {
            var headers = message.getMessageProperties();
            if (!MessageProperties.CONTENT_TYPE_JSON.equals(headers.getContentType())) {
                throw new IllegalArgumentException("到期消息必须为 JSON");
            }
            event = codec.decodeCreated(new String(message.getBody(), StandardCharsets.UTF_8));
            if (!event.eventId().equals(headers.getMessageId()) || !event.eventType().equals(headers.getType())) {
                throw new IllegalArgumentException("AMQP 标识与订单事件不符");
            }
        } catch (RuntimeException exception) {
            metrics.recordConsume("invalid");
            log.warn("拒绝无效到期消息 messageId={}", message.getMessageProperties().getMessageId(), exception);
            channel.basicReject(tag, false);
            return;
        }

        MDC.put("eventId", event.eventId());
        MDC.put("orderNo", event.orderNo());
        try {
            TimeoutMessageResult result;
            try {
                result = processWithRetry(event);
            } catch (RuntimeException exception) {
                metrics.recordConsume("failure");
                log.warn("到期消息失败 eventId={} orderNo={}", event.eventId(), event.orderNo(), exception);
                channel.basicReject(tag, false);
                return;
            }
            // 数据库代理已提交，或在回滚之后读到了成功记录；ACK 丢失允许 broker 再投递。
            // ACK 的 IOException 不捕获成业务失败，也不在同一个 channel 上再发矛盾的拒绝指令。
            channel.basicAck(tag, false);
            metrics.recordConsume(result.name().toLowerCase());
            log.info("到期消息已处理 eventId={} orderNo={} result={}", event.eventId(), event.orderNo(), result);
        } finally {
            MDC.remove("eventId");
            MDC.remove("orderNo");
        }
    }

    private TimeoutMessageResult processWithRetry(OrderCreatedEvent event) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            RuntimeException failure;
            try {
                return transactions.process(event);
            } catch (DuplicateKeyException exception) {
                try {
                    if (transactions.alreadyProcessed(event)) return TimeoutMessageResult.DUPLICATE;
                    throw exception;
                } catch (RuntimeException readFailure) {
                    failure = readFailure;
                }
            } catch (RuntimeException exception) {
                failure = exception;
            }
            if (attempt == 3 || !temporaryDatabaseFailure(failure)) throw failure;
            log.warn("到期消费暂时失败 eventId={} orderNo={} attempt={}",
                    event.eventId(), event.orderNo(), attempt, failure);
            try {
                // 上一个事务代理已退出；下一次调用开启新的 REQUIRED 事务。
                Thread.sleep(attempt * 1000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("到期消费重试被中断", exception);
            }
        }
        throw new IllegalStateException("到期消费尝试已耗尽");
    }

    private boolean temporaryDatabaseFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof TransientDataAccessException || current instanceof RecoverableDataAccessException
                    || current instanceof CannotCreateTransactionException) return true;
            if (current instanceof SQLException sql && sql.getSQLState() != null
                    && (sql.getSQLState().startsWith("08") || sql.getSQLState().startsWith("40"))) return true;
        }
        return false;
    }
}
