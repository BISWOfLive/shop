package com.chengli.shop.service;

import com.chengli.shop.dto.CursorProductQuery;
import com.chengli.shop.vo.CursorPage;
import com.chengli.shop.vo.ProductCardVO;

public interface ProductFeedService {
    CursorPage<ProductCardVO> feed(CursorProductQuery query);
}
