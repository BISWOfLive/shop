package com.chengli.shop.controller;
import com.chengli.shop.common.ApiResponse;
import com.chengli.shop.dto.*;
import com.chengli.shop.service.InventoryService;
import com.chengli.shop.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@Profile("dev")
@RequestMapping("/api/inventories")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService service;
    @GetMapping("/{productId}")
    public ApiResponse<InventoryVO> get(@PathVariable @Positive Long productId) { return ApiResponse.success(service.get(productId)); }
    @PostMapping("/stock-in")
    public ApiResponse<InventoryLogVO> stockIn(@RequestBody @Valid InventoryStockInRequest request) { return ApiResponse.success(service.stockIn(request)); }
    @PostMapping("/adjust")
    public ApiResponse<InventoryLogVO> adjust(@RequestBody @Valid InventoryAdjustRequest request) { return ApiResponse.success(service.adjust(request)); }
}
