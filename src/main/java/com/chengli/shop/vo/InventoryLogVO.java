package com.chengli.shop.vo;

import lombok.Data;
import java.time.LocalDateTime;

/** 管理端库存流水响应。 */
@Data
public class InventoryLogVO {
    private Long id;
    private Long productId;
    private String productName;
    private Long orderId;
    private Long actorId;
    private Integer changeType;
    private String bizNo;
    private Integer beforeQty;
    private Integer changeQty;
    private Integer afterQty;
    private String remark;
    private LocalDateTime createdAt;
}
