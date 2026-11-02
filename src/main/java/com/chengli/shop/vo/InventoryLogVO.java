package com.chengli.shop.vo;
import lombok.Data;
import java.time.OffsetDateTime;
@Data
public class InventoryLogVO {
    private String id;
    private String productId;
    private String productName;
    private String orderId;
    private String actorId;
    private Integer changeType;
    private String bizNo;
    private Integer beforeQty;
    private Integer changeQty;
    private Integer afterQty;
    private String remark;
    private OffsetDateTime createdAt;
}
