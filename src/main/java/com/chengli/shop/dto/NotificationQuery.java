package com.chengli.shop.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import jakarta.validation.constraints.Size;

/** 当前用户的通知查询，接收用户 ID 从身份上下文获取。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationQuery extends PageQuery {
    /** null：全部，true：未读，false：已读。 */
    private Boolean unread;

    @Size(max = 32, message = "订单号不能超过 32 个字符")
    private String orderNo;
}
