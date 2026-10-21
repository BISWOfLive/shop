package com.chengli.shop.controller;
import com.chengli.shop.common.*;
import com.chengli.shop.dto.InventoryLogQuery;
import com.chengli.shop.service.InventoryService;
import com.chengli.shop.vo.InventoryLogVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("dev")
@RequestMapping("/api/inventory-logs")
@RequiredArgsConstructor
public class InventoryLogController {
    private final InventoryService service;
    @GetMapping
    public ApiResponse<PageResult<InventoryLogVO>> logs(@Valid @ModelAttribute InventoryLogQuery query) {
        return ApiResponse.success(service.logs(query));
    }
}
