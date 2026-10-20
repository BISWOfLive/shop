package com.chengli.shop.controller;

import com.chengli.shop.common.ApiResponse;
import com.chengli.shop.common.PageResult;
import com.chengli.shop.dto.ProductQuery;
import com.chengli.shop.dto.ProductSaveRequest;
import com.chengli.shop.service.ProductService;
import com.chengli.shop.vo.ProductVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ApiResponse<PageResult<ProductVO>> page(@Valid @ModelAttribute ProductQuery query) {
        return ApiResponse.success(productService.page(query));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductVO> detail(@PathVariable("id") @Positive(message = "商品 ID 必须为正整数") Long id) {
        return ApiResponse.success(productService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductVO>> create(@Valid @RequestBody ProductSaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(productService.create(request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductVO> update(
            @PathVariable("id") @Positive(message = "商品 ID 必须为正整数") Long id,
            @Valid @RequestBody ProductSaveRequest request) {
        return ApiResponse.success(productService.update(id, request));
    }
}
