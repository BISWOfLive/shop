package com.chengli.shop.controller;
import com.chengli.shop.common.*;
import com.chengli.shop.dto.*;
import com.chengli.shop.service.OrderService;
import com.chengli.shop.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("dev")
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService service;
    @PostMapping
    public ResponseEntity<ApiResponse<OrderVO>> create(@Valid @RequestBody OrderCreateRequest request) {
        var result = service.create(request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(ApiResponse.success(result.order()));
    }
    @GetMapping public ApiResponse<PageResult<OrderSummaryVO>> page(@Valid @ModelAttribute OrderQuery query) { return ApiResponse.success(service.pageMine(query)); }
    @GetMapping("/by-request/{idempotencyKey}")
    public ApiResponse<OrderVO> byRequest(@PathVariable String idempotencyKey) { return ApiResponse.success(service.getMineByRequest(idempotencyKey)); }
    @GetMapping("/by-request")
    public ApiResponse<OrderVO> byRequestQuery(@RequestParam String idempotencyKey) { return ApiResponse.success(service.getMineByRequest(idempotencyKey)); }
    @GetMapping("/{orderNo}")
    public ApiResponse<OrderVO> get(@PathVariable String orderNo) { return ApiResponse.success(service.getMine(orderNo)); }
    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<OrderVO> cancel(@PathVariable String orderNo) { return ApiResponse.success(service.cancelMine(orderNo)); }
    @PostMapping("/{orderNo}/mock-pay")
    public ApiResponse<OrderVO> pay(@PathVariable String orderNo) { return ApiResponse.success(service.mockPayMine(orderNo)); }
}
