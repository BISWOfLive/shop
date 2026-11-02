package com.chengli.shop.service;

import com.chengli.shop.vo.ProductDetailVO;

/** 浏览详情入口；编辑和交易继续使用数据库服务。 */
public interface ProductDetailQueryService {
    ProductDetailVO getDetail(Long productId);
}
