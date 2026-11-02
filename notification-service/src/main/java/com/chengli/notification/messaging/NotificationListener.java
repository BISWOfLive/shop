package com.chengli.notification.messaging;

import com.chengli.notification.config.NotificationConsumerProperties;
import com.chengli.notification.config.NotificationRabbitConfiguration;
import com.chengli.notification.service.NotificationConsumerTransactionService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.CannotCreateTransactionException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

/**
 * 通知事件监听：校验消息格式与标识，业务事务提交后 ACK。
 * 格式/版本错误、内容冲突和耗尽重试的消息拒绝且不 requeue，进入失败队列；
 * 暂时数据库异常最多 3 次尝试，每次调用独立新事务。ACK 丢失允许 broker 重投，由幂等恢复。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "notification.consumer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class NotificationListener {
    private final OrderNotificationEventCodec codec;
    private final NotificationConsumerTransactionService transactions;
    private final NotificationConsumerProperties properties;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @RabbitListener(queues = NotificationRabbitConfiguration.WORK_QUEUE,
            containerFactory = NotificationRabbitConfiguration.LISTENER_FACTORY)
    public void receive(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        OrderNotificationEvent event;
        try {
            event = validate(message);
        } catch (RuntimeException exception) {
            recordConsume("invalid");
            log.warn("拒绝无效通知消息 messageId={} errorType={}",
                    message.getMessageProperties().getMessageId(), exception.getClass().getSimpleName());
            channel.basicReject(tag, false);
            return;
        }

        try {
            if (!processWithRetry(event)) return;
        } catch (RuntimeException exception) {
            recordConsume("failure");
            log.warn("通知消息失败 eventId={} orderNo={} errorType={}",
                    event.eventId(), event.orderNo(), exception.getClass().getSimpleName());
            channel.basicReject(tag, false);
            return;
        }
        // 事务代理已提交；这里的 ACK 失败会让 broker 重投，重复投递由稳定 eventId 与复合唯一键处理。
        channel.basicAck(tag, false);
        recordConsume("processed");
        log.info("通知消息已处理 eventId={} orderNo={}", event.eventId(), event.orderNo());
    }

    /** 只使用固定结果类型的消费指标，不携带 eventId、订单号或用户 ID。 */
    private void recordConsume(String result) {
        meterRegistry.counter("shop.notification.consume", "result", result).increment();
    }

    private OrderNotificationEvent validate(Message message) {
        var headers = message.getMessageProperties();
        if (!MessageProperties.CONTENT_TYPE_JSON.equals(headers.getContentType())) {
            throw new IllegalArgumentException("通知消息必须为 JSON");
        }
        byte[] body = message.getBody();
        if (body == null || body.length == 0 || body.length > properties.getMaxBodyBytes()) {
            throw new IllegalArgumentException("通知消息正文为空或超过大小上限");
        }
        var event = codec.decode(new String(body, StandardCharsets.UTF_8));
        if (!event.eventId().equals(headers.getMessageId())
                || !event.eventType().equals(headers.getType())) {
            throw new IllegalArgumentException("AMQP 标识与通知事件不符");
        }
        return event;
    }

    /** 返回 true 表示可 ACK（新处理或已提交的重复成功）；冲突且内容不一致直接抛出。 */
    private boolean processWithRetry(OrderNotificationEvent event) {
        for (int attempt = 1; ; attempt++) {
            RuntimeException failure;
            try {
                transactions.process(event);
                return true;
            } catch (DuplicateKeyException duplicate) {
                try {
                    if (transactions.alreadyProcessed(event)) {
                        log.info("重复通知事件按已提交处理 eventId={}", event.eventId());
                        return true;
                    }
                    throw duplicate;
                } catch (RuntimeException readFailure) {
                    failure = readFailure;
                }
            } catch (RuntimeException exception) {
                failure = exception;
            }
            if (attempt >= properties.getRetryAttempts() || !temporaryDatabaseFailure(failure)) {
                throw failure;
            }
            log.warn("通知消费暂时失败 eventId={} attempt={} errorType={}",
                    event.eventId(), attempt, failure.getClass().getSimpleName());
            try {
                // 上一个事务代理已退出；下一次调用开启新的 REQUIRED 事务。
                Thread.sleep(attempt * 1000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("通知消费重试被中断", interrupted);
            }
        }
    }

    private boolean temporaryDatabaseFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof TransientDataAccessException
                    || current instanceof RecoverableDataAccessException
                    || current instanceof CannotCreateTransactionException) {
                return true;
            }
            if (current instanceof SQLException sql && sql.getSQLState() != null
                    && (sql.getSQLState().startsWith("08") || sql.getSQLState().startsWith("40"))) {
                return true;
            }
        }
        return false;
    }
}
