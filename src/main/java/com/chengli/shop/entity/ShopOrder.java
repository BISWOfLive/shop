package com.chengli.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * 订单主表；金额、状态、到期时间和幂等摘要均由服务端生成。
 */
@Data
@TableName("shop_order")
public class ShopOrder {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("order_no")
    private String orderNo;

    /** 从服务端登录身份获取，不接收客户端指定买家。 */
    @TableField("buyer_id")
    private Long buyerId;

    @TableField("idempotency_key")
    private String idempotencyKey;

    /** 规范化商品与数量的 SHA-256 摘要。 */
    @TableField("request_hash")
    private String requestHash;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    /** 0：待支付，1：已支付，2：已取消。 */
    private Integer status = 0;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("paid_at")
    private LocalDateTime paidAt;

    @TableField("cancelled_at")
    private LocalDateTime cancelledAt;

    /** USER：主动取消，TIMEOUT：到期取消。 */
    @TableField("cancel_reason")
    private String cancelReason;

    @TableField(value = "created_at", fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
