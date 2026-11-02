package com.chengli.shop.vo;

import lombok.Data;

import java.time.OffsetDateTime;

/** 当前登录用户的通知响应；对外 ID 为十进制字符串，时间带 +08:00 偏移。 */
@Data
public class NotificationVO {
    private String id;
    private String orderNo;
    private String title;
    private String content;
    private OffsetDateTime readAt;
    private OffsetDateTime createdAt;
}
