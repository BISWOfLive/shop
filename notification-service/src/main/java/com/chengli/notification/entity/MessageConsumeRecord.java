package com.chengli.notification.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知服务自己的成功消费记录，复合主键 (consumer_name,event_id)。
 * 与 notification 同库同事务写入；不使用单 ID 方法。
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

    @TableField("processed_at")
    private LocalDateTime processedAt;
}
