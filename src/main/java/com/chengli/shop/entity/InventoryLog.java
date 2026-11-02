package com.chengli.shop.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 只追加的库存流水；余额和操作人由服务端生成。
 */
@Data
@TableName("inventory_log")
public class InventoryLog {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("product_id")
    private Long productId;

    @TableField("order_id")
    private Long orderId;

    @TableField("actor_id")
    private Long actorId;

    /** 1：入库，2：下单扣减，3：取消归还，4：人工调整。 */
    @TableField("change_type")
    private Integer changeType;

    @TableField("biz_no")
    private String bizNo;

    @TableField("before_qty")
    private Integer beforeQty;

    @TableField("change_qty")
    private Integer changeQty;

    @TableField("after_qty")
    private Integer afterQty;

    private String remark = "";

    @TableField(value = "created_at", fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;
}
