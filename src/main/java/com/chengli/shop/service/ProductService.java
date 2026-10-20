package com.chengli.shop.service;

import com.chengli.shop.common.PageResult;
import com.chengli.shop.dto.ProductQuery;
import com.chengli.shop.dto.ProductSaveRequest;
import com.chengli.shop.vo.ProductVO;

public interface ProductService {
    PageResult<ProductVO> page(ProductQuery query);

    ProductVO getById(Long id);

    ProductVO create(ProductSaveRequest request);

    ProductVO update(Long id, ProductSaveRequest request);
}
