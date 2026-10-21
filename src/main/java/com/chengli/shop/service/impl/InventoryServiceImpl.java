package com.chengli.shop.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengli.shop.common.PageResult;
import com.chengli.shop.dto.*;
import com.chengli.shop.entity.*;
import com.chengli.shop.enums.InventoryChangeType;
import com.chengli.shop.exception.BusinessException;
import com.chengli.shop.mapper.*;
import com.chengli.shop.service.InventoryService;
import com.chengli.shop.service.transaction.InventoryTransactionService;
import com.chengli.shop.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {
    private final ProductMapper productMapper;
    private final InventoryMapper inventoryMapper;
    private final InventoryLogMapper logMapper;
    private final InventoryTransactionService transactions;

    @Override public InventoryVO get(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        Inventory inventory = inventoryMapper.selectById(productId);
        if (inventory == null) throw new BusinessException(HttpStatus.CONFLICT, "商品库存尚未准备");
        var vo = new InventoryVO();
        vo.setProductId(productId.toString()); vo.setProductName(product.getName());
        vo.setImageUrl(product.getImageUrl()); vo.setAvailableQty(inventory.getAvailableQty());
        vo.setUpdatedAt(inventory.getUpdatedAt().atOffset(ZoneOffset.ofHours(8)));
        return vo;
    }

    @Override public InventoryLogVO stockIn(InventoryStockInRequest r) {
        if (r.getQuantity() == null || r.getQuantity() <= 0) throw invalid("入库数量必须大于 0");
        return maintain(r.getProductId(), r.getQuantity(), InventoryChangeType.STOCK_IN, r.getBizNo(), r.getRemark());
    }
    @Override public InventoryLogVO adjust(InventoryAdjustRequest r) {
        if (r.getChangeQty() == null || r.getChangeQty() == 0) throw invalid("调整数量不能为 0");
        return maintain(r.getProductId(), r.getChangeQty(), InventoryChangeType.MANUAL_ADJUST, r.getBizNo(), r.getRemark());
    }
    private InventoryLogVO maintain(Long productId, int delta, InventoryChangeType type, String rawNo, String rawRemark) {
        if (productId == null || productId <= 0) throw invalid("商品 ID 必须为正整数");
        String no = rawNo == null ? "" : rawNo.strip();
        String remark = rawRemark == null ? "" : rawRemark.strip();
        if (!no.matches("[\\x21-\\x7E]{1,64}") || remark.length() > 255) throw invalid("业务号或备注格式不正确");
        InventoryLog existing = find(type, no, productId);
        if (existing != null) return same(existing, delta, remark);
        InventoryLog saved;
        try {
            // 异常到达这里时，独立事务代理已完成回滚，随后才能读取胜出的已提交流水。
            saved = transactions.change(productId, delta, type, no, null, null, remark);
        } catch (DuplicateKeyException exception) {
            existing = find(type, no, productId);
            if (existing == null) throw exception;
            return same(existing, delta, remark);
        }
        Product p = productMapper.selectById(productId);
        return toVO(saved, p == null ? null : p.getName());
    }
    private InventoryLog find(InventoryChangeType type, String no, Long productId) {
        return logMapper.selectOne(Wrappers.<InventoryLog>lambdaQuery().eq(InventoryLog::getChangeType, type.getCode())
                .eq(InventoryLog::getBizNo, no).eq(InventoryLog::getProductId, productId));
    }
    private InventoryLogVO same(InventoryLog log, int delta, String remark) {
        if (log.getChangeQty() != delta || !Objects.equals(log.getRemark(), remark))
            throw new BusinessException(HttpStatus.CONFLICT, "业务号已用于不同的库存变更");
        Product p = productMapper.selectById(log.getProductId());
        return toVO(log, p == null ? null : p.getName());
    }
    @Override public PageResult<InventoryLogVO> logs(InventoryLogQuery q) {
        var page = new Page<InventoryLog>(q.getPage(), q.getSize());
        logMapper.selectPage(page, Wrappers.<InventoryLog>lambdaQuery()
                .eq(q.getProductId() != null, InventoryLog::getProductId, q.getProductId())
                .eq(q.getOrderId() != null, InventoryLog::getOrderId, q.getOrderId())
                .eq(q.getChangeType() != null, InventoryLog::getChangeType, q.getChangeType())
                .eq(q.getBizNo() != null && !q.getBizNo().isBlank(), InventoryLog::getBizNo, q.getBizNo())
                .orderByDesc(InventoryLog::getCreatedAt, InventoryLog::getId));
        Map<Long, String> names = page.getRecords().isEmpty() ? Map.of() :
                productMapper.selectList(Wrappers.<Product>lambdaQuery().select(Product::getId, Product::getName)
                        .in(Product::getId, page.getRecords().stream().map(InventoryLog::getProductId).collect(Collectors.toSet())))
                        .stream().collect(Collectors.toMap(Product::getId, Product::getName));
        return new PageResult<>(page.getRecords().stream().map(l -> toVO(l, names.get(l.getProductId()))).toList(),
                page.getTotal(), q.getPage(), q.getSize());
    }
    private InventoryLogVO toVO(InventoryLog log, String name) {
        var vo = new InventoryLogVO();
        vo.setId(log.getId().toString()); vo.setProductId(log.getProductId().toString()); vo.setProductName(name);
        vo.setOrderId(log.getOrderId() == null ? null : log.getOrderId().toString());
        vo.setActorId(log.getActorId() == null ? null : log.getActorId().toString());
        vo.setChangeType(log.getChangeType()); vo.setBizNo(log.getBizNo());
        vo.setBeforeQty(log.getBeforeQty()); vo.setChangeQty(log.getChangeQty()); vo.setAfterQty(log.getAfterQty());
        vo.setRemark(log.getRemark()); vo.setCreatedAt(log.getCreatedAt().atOffset(ZoneOffset.ofHours(8)));
        return vo;
    }
    private BusinessException invalid(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, message); }
}
