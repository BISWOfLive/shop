package com.chengli.shop.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengli.shop.common.PageResult;
import com.chengli.shop.dto.*;
import com.chengli.shop.entity.*;
import com.chengli.shop.exception.BusinessException;
import com.chengli.shop.identity.CurrentBuyerProvider;
import com.chengli.shop.mapper.*;
import com.chengli.shop.service.OrderService;
import com.chengli.shop.service.order.*;
import com.chengli.shop.service.transaction.OrderTransactionService;
import com.chengli.shop.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Profile("dev")
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final CurrentBuyerProvider buyerProvider;
    private final OrderRequestNormalizer normalizer;
    private final OrderTransactionService transactions;
    private final ShopOrderMapper orderMapper;
    private final OrderItemMapper itemMapper;

    @Override public OrderCreateResult create(OrderCreateRequest request) {
        Long buyerId = buyerProvider.currentBuyerId();
        var command = normalizer.normalize(request);
        ShopOrder existing = byRequest(buyerId, command.idempotencyKey());
        if (existing != null) return previous(existing, command.requestHash());
        try {
            var created = transactions.place(buyerId, command, UUID.randomUUID().toString().replace("-", ""));
            return new OrderCreateResult(created, true);
        } catch (DuplicateKeyException exception) {
            // place 的代理已回滚，只有同买家同请求号的已提交记录能恢复为幂等结果。
            existing = byRequest(buyerId, command.idempotencyKey());
            if (existing == null) throw exception;
            return previous(existing, command.requestHash());
        }
    }
    private OrderCreateResult previous(ShopOrder existing, String hash) {
        if (!existing.getRequestHash().equals(hash)) throw new BusinessException(HttpStatus.CONFLICT, "下单请求号已用于不同的商品或数量");
        return new OrderCreateResult(detail(existing), false);
    }
    @Override @Transactional(readOnly = true)
    public PageResult<OrderSummaryVO> pageMine(OrderQuery q) {
        Long buyerId = buyerProvider.currentBuyerId();
        var page = new Page<ShopOrder>(q.getPage(), q.getSize());
        orderMapper.selectPage(page, Wrappers.<ShopOrder>lambdaQuery().eq(ShopOrder::getBuyerId, buyerId)
                .eq(q.getOrderNo() != null && !q.getOrderNo().isBlank(), ShopOrder::getOrderNo, q.getOrderNo())
                .eq(q.getStatus() != null, ShopOrder::getStatus, q.getStatus())
                .orderByDesc(ShopOrder::getCreatedAt, ShopOrder::getId));
        return new PageResult<>(page.getRecords().stream().map(OrderViews::summary).toList(), page.getTotal(), q.getPage(), q.getSize());
    }
    @Override @Transactional(readOnly = true)
    public OrderVO getMine(String no) {
        var order = orderMapper.selectOne(Wrappers.<ShopOrder>lambdaQuery()
                .eq(ShopOrder::getBuyerId, buyerProvider.currentBuyerId()).eq(ShopOrder::getOrderNo, no));
        return detail(require(order));
    }
    @Override @Transactional(readOnly = true)
    public OrderVO getMineByRequest(String key) { return detail(require(byRequest(buyerProvider.currentBuyerId(), key))); }
    @Override public OrderVO cancelMine(String no) { return transactions.cancel(buyerProvider.currentBuyerId(), no); }
    @Override public OrderVO mockPayMine(String no) { return transactions.mockPay(buyerProvider.currentBuyerId(), no); }
    private ShopOrder byRequest(Long buyerId, String key) {
        return orderMapper.selectOne(Wrappers.<ShopOrder>lambdaQuery()
                .eq(ShopOrder::getBuyerId, buyerId).eq(ShopOrder::getIdempotencyKey, key));
    }
    private ShopOrder require(ShopOrder order) {
        if (order == null) throw new BusinessException(HttpStatus.NOT_FOUND, "订单不存在");
        return order;
    }
    private OrderVO detail(ShopOrder order) {
        return OrderViews.detail(order, itemMapper.selectList(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getOrderId, order.getId()).orderByAsc(OrderItem::getProductId)));
    }
}
