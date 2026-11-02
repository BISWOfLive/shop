package com.chengli.shop.service.messaging;

public record PublishOutcome(Status status, String error) {
    public enum Status { CONFIRMED_ROUTED, FAILED }

    public static PublishOutcome confirmed() {
        return new PublishOutcome(Status.CONFIRMED_ROUTED, "");
    }

    public static PublishOutcome failed(String error) {
        return new PublishOutcome(Status.FAILED, summarize(error));
    }

    public static String summarize(String error) {
        String text = error == null || error.isBlank() ? "消息投递失败" : error.replace('\r', ' ').replace('\n', ' ');
        int count = text.codePointCount(0, text.length());
        return count <= 1000 ? text : text.substring(0, text.offsetByCodePoints(0, 1000));
    }
}
