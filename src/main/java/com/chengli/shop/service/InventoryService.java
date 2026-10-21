package com.chengli.shop.service;
import com.chengli.shop.common.PageResult;
import com.chengli.shop.dto.*;
import com.chengli.shop.vo.*;
public interface InventoryService {
    InventoryVO get(Long productId);
    InventoryLogVO stockIn(InventoryStockInRequest request);
    InventoryLogVO adjust(InventoryAdjustRequest request);
    PageResult<InventoryLogVO> logs(InventoryLogQuery query);
}
