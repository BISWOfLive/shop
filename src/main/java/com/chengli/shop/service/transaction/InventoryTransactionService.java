package com.chengli.shop.service.transaction;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chengli.shop.entity.*;
import com.chengli.shop.enums.InventoryChangeType;
import com.chengli.shop.exception.BusinessException;
import com.chengli.shop.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryTransactionService {
    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper logMapper;
    private final ProductMapper productMapper;
    private final DatabaseTimeMapper timeMapper;

    @Transactional
    public InventoryLog change(Long productId, int delta, InventoryChangeType type, String bizNo,
                               Long orderId, Long actorId, String remark) {
        if (productMapper.selectById(productId) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        Inventory inventory = inventoryMapper.selectById(productId);
        if (inventory == null) throw conflict("商品库存尚未准备");
        int before = inventory.getAvailableQty();
        long after = (long) before + delta;
        if (delta == 0 || after < 0 || after > Integer.MAX_VALUE) throw conflict("库存不足或数量超出范围");
        var update = Wrappers.<Inventory>lambdaUpdate()
                .eq(Inventory::getProductId, productId).eq(Inventory::getAvailableQty, before)
                .ge(delta < 0, Inventory::getAvailableQty, -(long) delta)
                .set(Inventory::getAvailableQty, (int) after)
                .setSql("updated_at = CURRENT_TIMESTAMP(3)");
        if (inventoryMapper.update(null, update) != 1) throw conflict("库存已变化，请重新提交");
        InventoryLog log = new InventoryLog();
        log.setProductId(productId); log.setOrderId(orderId); log.setActorId(actorId);
        log.setChangeType(type.getCode()); log.setBizNo(bizNo);
        log.setBeforeQty(before); log.setChangeQty(delta); log.setAfterQty((int) after);
        log.setRemark(remark); log.setCreatedAt(timeMapper.currentTime());
        logMapper.insert(log);
        return log;
    }
    private BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }
}
