package com.chengli.shop.service.order;

import com.chengli.shop.entity.*;
import com.chengli.shop.vo.*;
import java.time.*;
import java.util.List;

public final class OrderViews {
    private OrderViews() {}
    public static OrderSummaryVO summary(ShopOrder order) {
        var vo = new OrderSummaryVO();
        fill(order, vo);
        return vo;
    }
    public static OrderVO detail(ShopOrder order, List<OrderItem> items) {
        var vo = new OrderVO();
        fill(order, vo);
        vo.setBuyerId(order.getBuyerId().toString());
        vo.setItems(items.stream().map(item -> {
            var line = new OrderItemVO();
            line.setId(item.getId().toString()); line.setProductId(item.getProductId().toString());
            line.setProductName(item.getProductName()); line.setProductImageUrl(item.getProductImageUrl());
            line.setUnitPrice(item.getUnitPrice().toPlainString()); line.setQuantity(item.getQuantity());
            line.setLineAmount(item.getLineAmount().toPlainString());
            return line;
        }).toList());
        return vo;
    }
    private static void fill(ShopOrder order, OrderSummaryVO vo) {
        vo.setId(order.getId().toString()); vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getTotalAmount().toPlainString()); vo.setStatus(order.getStatus());
        vo.setExpiresAt(time(order.getExpiresAt())); vo.setPaidAt(time(order.getPaidAt()));
        vo.setCancelledAt(time(order.getCancelledAt())); vo.setCancelReason(order.getCancelReason());
        vo.setCreatedAt(time(order.getCreatedAt())); vo.setUpdatedAt(time(order.getUpdatedAt()));
    }
    private static OffsetDateTime time(LocalDateTime time) {
        return time == null ? null : time.atOffset(ZoneOffset.ofHours(8));
    }
}
