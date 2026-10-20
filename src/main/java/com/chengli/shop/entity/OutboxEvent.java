package com.chengli.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 事务事件和消息投递任务；领取与释放租约时由条件更新维护状态。
 */
@Data
@TableName("outbox_event")
public class OutboxEvent {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("event_id")
    private String eventId;

    @TableField("aggregate_type")
    private String aggregateType;

    @TableField("aggregate_id")
    private Long aggregateId;

    @TableField("event_type")
    private String eventType;

    @TableField("payload_version")
    private Integer payloadVersion = 1;

    /** JSON 文本，对应 MySQL JSON 列；序列化由消息发布逻辑负责。 */
    private String payload;

    /** 0：待发送，1：发送中，2：已发送，3：停止重试。 */
    @TableField("publish_status")
    private Integer publishStatus = 0;

    @TableField("retry_count")
    private Integer retryCount = 0;

    @TableField("available_at")
    private LocalDateTime availableAt;

    @TableField("locked_by")
    private String lockedBy;

    @TableField("locked_until")
    private LocalDateTime lockedUntil;

    @TableField("last_error")
    private String lastError = "";

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
