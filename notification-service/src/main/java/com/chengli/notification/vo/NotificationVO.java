package com.chengli.notification.vo;

import java.time.OffsetDateTime;

/** 本人通知响应：字符串 ID、带 +08:00 偏移的读取与落库时间；正文为纯文本。 */
public record NotificationVO(String id, String orderNo, String title, String content,
                             OffsetDateTime readAt, OffsetDateTime createdAt) {
}
