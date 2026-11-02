package com.chengli.notification.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 通知事件消费参数。默认关闭；显式启用后才装配 Rabbit 连接、拓扑与监听容器。
 * 查询接口不受该开关影响，消费者关闭不删除已经保存的通知。
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "notification.consumer")
public class NotificationConsumerProperties {
    private boolean enabled = false;
    @Min(1) @Max(32)
    private int concurrency = 2;
    @Min(1) @Max(100)
    private int prefetch = 10;
    /** 消息正文上限 16 KiB，超出直接拒绝到失败队列。 */
    @Min(1024) @Max(1048576)
    private int maxBodyBytes = 16384;
    /** 暂时数据库异常的总尝试次数，间隔 1s、2s，每次调用独立事务。 */
    @Min(1) @Max(5)
    private int retryAttempts = 3;
    @Valid
    private Rabbit rabbit = new Rabbit();

    @Data
    public static class Rabbit {
        @NotBlank
        private String host = "127.0.0.1";
        @Min(1) @Max(65535)
        private int port = 5672;
        @NotBlank
        private String virtualHost = "/";
        private String username = "";
        private String password = "";
        @Min(1) @Max(10000)
        private int connectionTimeoutMillis = 5000;
    }
}
