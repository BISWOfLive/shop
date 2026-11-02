package com.chengli.notification.vo;

/** 未读数：非负十进制字符串，避免前端把大整型精度问题当成数值误差。 */
public record UnreadNotificationCountVO(String count) {
}
