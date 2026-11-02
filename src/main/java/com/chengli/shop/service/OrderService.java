package com.chengli.shop.service;
import com.chengli.shop.common.PageResult;
import com.chengli.shop.dto.*;
import com.chengli.shop.service.order.OrderCreateResult;
import com.chengli.shop.vo.*;
public interface OrderService {
    OrderCreateResult create(OrderCreateRequest request);
    PageResult<OrderSummaryVO> pageMine(OrderQuery query);
    OrderVO getMine(String orderNo);
    OrderVO getMineByRequest(String idempotencyKey);
    OrderVO cancelMine(String orderNo);
    OrderVO mockPayMine(String orderNo);
}
