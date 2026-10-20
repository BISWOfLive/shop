package com.chengli.shop.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 事务消息投递状态。
 */
@Getter
@RequiredArgsConstructor
public enum OutboxPublishStatus {
    PENDING(0, "待发送"),
    PUBLISHING(1, "发送中"),
    PUBLISHED(2, "已发送"),
    STOPPED(3, "停止重试");

    @EnumValue
    private final int code;
    private final String label;
}
