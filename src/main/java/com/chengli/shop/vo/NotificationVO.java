package com.chengli.shop.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 当前登录用户的通知响应。 */
@Data
public class NotificationVO {
    private Long id;
    private String orderNo;
    private String title;
    private String content;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
