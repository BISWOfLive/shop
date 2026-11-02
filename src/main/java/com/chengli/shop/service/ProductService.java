package com.chengli.shop.service;

import com.chengli.shop.common.PageResult;
import com.chengli.shop.dto.ProductQuery;
import com.chengli.shop.dto.ProductSaveRequest;
import com.chengli.shop.vo.ProductVO;
import com.chengli.shop.vo.ProductDetailVO;

public interface ProductService {

    /** 公开商品列表：服务端强制上架范围，客户端不能扩大到下架商品。 */
    PageResult<ProductVO> pagePublic(ProductQuery query);

    /** 管理端商品列表：允许按上下架状态筛选。 */
    PageResult<ProductVO> pageAdmin(ProductQuery query);

    ProductVO getById(Long id);

    ProductDetailVO getDetail(Long id);

    ProductDetailVO create(ProductSaveRequest request);

    ProductDetailVO update(Long id, ProductSaveRequest request);
}
