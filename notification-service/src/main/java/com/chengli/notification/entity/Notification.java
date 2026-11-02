package com.chengli.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 通知实体；readAt 为空表示未读，createdAt 由通知库时间在落库事务中写入。 */
@Data
@TableName("notification")
public class Notification {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("event_id")
    private String eventId;

    @TableField("recipient_user_id")
    private Long recipientUserId;

    @TableField("order_no")
    private String orderNo;

    private String title;

    private String content;

    @TableField("read_at")
    private LocalDateTime readAt;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
