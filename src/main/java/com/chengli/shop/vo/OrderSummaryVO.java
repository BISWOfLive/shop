package com.chengli.shop.vo;
import lombok.Data;
import java.time.OffsetDateTime;
@Data
public class OrderSummaryVO {
    private String id;
    private String orderNo;
    private String totalAmount;
    private Integer status;
    private OffsetDateTime expiresAt;
    private OffsetDateTime paidAt;
    private OffsetDateTime cancelledAt;
    private String cancelReason;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
