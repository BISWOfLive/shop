package com.chengli.shop.service.order;
import com.chengli.shop.vo.OrderVO;
public record OrderCreateResult(OrderVO order, boolean created) {}
