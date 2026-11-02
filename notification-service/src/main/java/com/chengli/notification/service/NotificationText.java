package com.chengli.notification.service;

/** 固定模板生成的标题与纯文本正文；正文不使用 v-html 或任何标记。 */
public record NotificationText(String title, String content) {
}
