package com.chengli.shop.service.transaction;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chengli.shop.entity.*;
import com.chengli.shop.enums.InventoryChangeType;
import com.chengli.shop.exception.BusinessException;
import com.chengli.shop.mapper.*;
import com.chengli.shop.service.messaging.OrderCreatedEventRecorder;
import com.chengli.shop.service.messaging.OrderNotificationEventRecorder;
import com.chengli.shop.service.order.*;
import com.chengli.shop.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderTransactionService {
    private final ShopOrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final ProductMapper productMapper;
    private final DatabaseTimeMapper timeMapper;
    private final InventoryTransactionService inventory;
    private final OrderCreatedEventRecorder eventRecorder;
    private final OrderNotificationEventRecorder notificationEvents;

    @Transactional
    public OrderVO place(Long buyerId, NormalizedOrderCommand command, String orderNo) {
        var ids = command.items().stream().map(NormalizedOrderCommand.Item::productId).toList();
        var products = productMapper.selectList(Wrappers.<Product>lambdaQuery().in(Product::getId, ids))
                .stream().collect(Collectors.toMap(Product::getId, p -> p));
        BigDecimal total = BigDecimal.ZERO;
        for (var requested : command.items()) {
            var product = products.get(requested.productId());
            if (product == null) throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
            if (!Integer.valueOf(1).equals(product.getStatus())) throw conflict("商品已下架");
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(requested.quantity())));
        }
        var now = timeMapper.currentTime();
        var order = new ShopOrder();
        order.setOrderNo(orderNo); order.setBuyerId(buyerId); order.setIdempotencyKey(command.idempotencyKey());
        order.setRequestHash(command.requestHash()); order.setTotalAmount(total);
        order.setCreatedAt(now); order.setUpdatedAt(now); order.setExpiresAt(now.plusMinutes(30));
        // 先占用主单请求唯一键，再按 normalizer 的商品 ID 数值升序取得库存锁。
        orderMapper.insert(order);
        for (var requested : command.items()) {
            var product = products.get(requested.productId());
            inventory.change(product.getId(), -requested.quantity(), InventoryChangeType.ORDER_DEDUCT,
                    orderNo, order.getId(), null, "");
            var item = new OrderItem();
            item.setOrderId(order.getId()); item.setProductId(product.getId());
            item.setProductName(product.getName()); item.setProductImageUrl(product.getImageUrl());
            item.setUnitPrice(product.getPrice()); item.setQuantity(requested.quantity()); item.setCreatedAt(now);
            itemMapper.insert(item);
        }
        var lines = items(order.getId());
        BigDecimal persistedTotal = lines.stream().map(OrderItem::getLineAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(persistedTotal) != 0) throw conflict("订单明细金额不一致");
        eventRecorder.append(order);
        return OrderViews.detail(orderMapper.selectById(order.getId()), lines);
    }

    @Transactional
    public OrderVO cancel(Long buyerId, String orderNo) {
        ShopOrder order = mine(buyerId, orderNo);
        if (order.getStatus() == 2) return OrderViews.detail(order, items(order.getId()));
        if (order.getStatus() != 0) throw conflict("已支付订单不能取消");
        int updated = orderMapper.update(null, Wrappers.<ShopOrder>lambdaUpdate()
                .eq(ShopOrder::getId, order.getId()).eq(ShopOrder::getBuyerId, buyerId).eq(ShopOrder::getStatus, 0)
                .set(ShopOrder::getStatus, 2).set(ShopOrder::getCancelReason, "USER").set(ShopOrder::getPaidAt, null)
                .setSql("cancelled_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)"));
        if (updated != 1) throw conflict("订单状态已变化，请重新读取");
        var lines = items(order.getId());
        returnStock(order, lines);
        // 库存归还成功后才读取持锁订单的最新终态并追加取消事件；失败则整个取消事务回滚。
        var persisted = mine(buyerId, orderNo);
        notificationEvents.appendCancelled(persisted);
        return OrderViews.detail(persisted, lines);
    }

    @Transactional
    public TimeoutCancelResult cancelExpired(String orderNo) {
        var order = orderMapper.selectOne(Wrappers.<ShopOrder>lambdaQuery()
                .eq(ShopOrder::getOrderNo, orderNo).last("FOR UPDATE"));
        if (order == null) return TimeoutCancelResult.NOT_FOUND;
        if (order.getStatus() != 0) return TimeoutCancelResult.ALREADY_TERMINAL;
        int updated = orderMapper.update(null, Wrappers.<ShopOrder>lambdaUpdate()
                .eq(ShopOrder::getId, order.getId()).eq(ShopOrder::getStatus, 0)
                .apply("expires_at <= CURRENT_TIMESTAMP(3)")
                .set(ShopOrder::getStatus, 2).set(ShopOrder::getCancelReason, "TIMEOUT")
                .set(ShopOrder::getPaidAt, null)
                .setSql("cancelled_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)"));
        if (updated == 0) return TimeoutCancelResult.NOT_DUE;
        if (updated != 1) throw conflict("到期订单状态更新失败");
        var lines = items(order.getId());
        returnStock(order, lines);
        // 同一本地事务内重新读取持锁订单；事件时间取已写入的 cancelled_at，不用扫描时间。
        var persisted = orderMapper.selectOne(Wrappers.<ShopOrder>lambdaQuery()
                .eq(ShopOrder::getId, order.getId()).last("FOR UPDATE"));
        notificationEvents.appendCancelled(persisted);
        return TimeoutCancelResult.CANCELLED;
    }

    @Transactional
    public OrderVO mockPay(Long buyerId, String orderNo) {
        ShopOrder order = mine(buyerId, orderNo);
        if (order.getStatus() == 1) return OrderViews.detail(order, items(order.getId()));
        if (order.getStatus() != 0) throw conflict("已取消订单不能支付");
        int updated = orderMapper.update(null, Wrappers.<ShopOrder>lambdaUpdate()
                .eq(ShopOrder::getId, order.getId()).eq(ShopOrder::getBuyerId, buyerId).eq(ShopOrder::getStatus, 0)
                .apply("expires_at > CURRENT_TIMESTAMP(3)")
                .set(ShopOrder::getStatus, 1).set(ShopOrder::getCancelledAt, null).set(ShopOrder::getCancelReason, null)
                .setSql("paid_at = CURRENT_TIMESTAMP(3), updated_at = CURRENT_TIMESTAMP(3)"));
        if (updated != 1) throw conflict("订单已到期或状态已变化，请重新读取");
        // 已写入 paid_at 后重新读取持锁订单，事件时间取数据库终态时间而不是请求时间。
        var persisted = mine(buyerId, orderNo);
        notificationEvents.appendPaid(persisted);
        return OrderViews.detail(persisted, items(order.getId()));
    }
    private ShopOrder mine(Long buyerId, String orderNo) {
        // 支付和取消在同一主单锁下判断最新状态；后续库存锁沿用明细的数值升序。
        var order = orderMapper.selectOne(Wrappers.<ShopOrder>lambdaQuery()
                .eq(ShopOrder::getBuyerId, buyerId).eq(ShopOrder::getOrderNo, orderNo).last("FOR UPDATE"));
        if (order == null) throw new BusinessException(HttpStatus.NOT_FOUND, "订单不存在");
        return order;
    }
    private void returnStock(ShopOrder order, List<OrderItem> lines) {
        for (var item : lines) inventory.change(item.getProductId(), item.getQuantity(),
                InventoryChangeType.ORDER_RETURN, order.getOrderNo(), order.getId(), null, "");
    }
    private List<OrderItem> items(Long orderId) {
        return itemMapper.selectList(Wrappers.<OrderItem>lambdaQuery().eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getProductId));
    }
    private BusinessException conflict(String message) { return new BusinessException(HttpStatus.CONFLICT, message); }
}