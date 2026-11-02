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
 * 单仓、单规格商品的可售库存。
 */
@Data
@TableName("inventory")
public class Inventory {
    /** 使用商品 ID 作为主键，由业务代码提供。 */
    @TableId(value = "product_id", type = IdType.INPUT)
    private Long productId;

    @TableField("available_qty")
    private Integer availableQty = 0;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
