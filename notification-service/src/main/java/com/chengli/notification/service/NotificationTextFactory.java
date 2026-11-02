package com.chengli.notification.service;

import com.chengli.notification.messaging.OrderNotificationEvent;
import org.springframework.stereotype.Component;

/**
 * 服务内部固定通知文案：标题分别“支付成功”“订单已取消”，正文使用事件快照中的
 * 订单号、金额和取消原因，不读取订单库、商品数据或客户端可编辑内容。
 */
@Component
public class NotificationTextFactory {

    public NotificationText create(OrderNotificationEvent event) {
        if (event == null) throw new IllegalArgumentException("通知事件不能为空");
        if ("ORDER_PAID".equals(event.eventType())) {
            return new NotificationText("支付成功",
                    "订单 " + event.orderNo() + " 支付成功，金额 " + event.totalAmount() + " 元。");
        }
        if ("ORDER_CANCELLED".equals(event.eventType())) {
            // 编码器已校验；这里防御未来绕过校验的调用方，避免把缺失原因写成“主动取消”。
            if (!"TIMEOUT".equals(event.cancelReason()) && !"USER".equals(event.cancelReason())) {
                throw new IllegalArgumentException("取消通知的原因无效");
            }
            String reason = "TIMEOUT".equals(event.cancelReason()) ? "到期取消" : "主动取消";
            return new NotificationText("订单已取消",
                    "订单 " + event.orderNo() + " 已取消（" + reason + "），金额 "
                            + event.totalAmount() + " 元。");
        }
        // 编码器已经拒绝未知类型；这里防御未来绕过校验的调用方。
        throw new IllegalArgumentException("未知通知事件类型");
    }
}
