package com.chengli.notification.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 本人通知查询；接收人只来自内部凭据，不由请求参数传入。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NotificationQuery extends PageQuery {
    /** null：全部，true：未读，false：已读。 */
    private Boolean unread;

    @Size(max = 32, message = "订单号不能超过 32 个字符")
    private String orderNo;
}
