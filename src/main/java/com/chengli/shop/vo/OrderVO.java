package com.chengli.shop.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 订单响应；不暴露内部请求摘要。 */
@Data
public class OrderVO {
    private Long id;
    private String orderNo;
    private Long buyerId;
    private BigDecimal totalAmount;
    private Integer status;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<OrderItemVO> items = List.of();
}
