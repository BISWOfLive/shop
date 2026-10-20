package com.chengli.shop.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 复合主键为 (consumerName, eventId)，记录成功消费的业务事件。
 * 消费记录与业务数据在同一事务内写入；不使用单 ID 方法。
 */
@Data
@TableName("message_consume_record")
public class MessageConsumeRecord {
    @TableField("consumer_name")
    private String consumerName;

    @TableField("event_id")
    private String eventId;

    @TableField("event_type")
    private String eventType;

    /** 未显式指定时使用数据库默认时间。 */
    @TableField("processed_at")
    private LocalDateTime processedAt;
}
