package com.chengli.shop.vo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderVO extends OrderSummaryVO {
    private String buyerId;
    private List<OrderItemVO> items = List.of();
}
