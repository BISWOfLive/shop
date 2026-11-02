package com.chengli.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengli.shop.dto.CursorProductQuery;
import com.chengli.shop.entity.Category;
import com.chengli.shop.entity.Product;
import com.chengli.shop.mapper.CategoryMapper;
import com.chengli.shop.mapper.ProductMapper;
import com.chengli.shop.service.ProductFeedCursorCodec;
import com.chengli.shop.service.ProductFeedCursorCodec.Position;
import com.chengli.shop.service.ProductFeedService;
import com.chengli.shop.vo.CursorPage;
import com.chengli.shop.vo.ProductCardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductFeedServiceImpl implements ProductFeedService {
    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ProductFeedCursorCodec cursorCodec;

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ProductCardVO> feed(CursorProductQuery query) {
        Position position = cursorCodec.decode(query);
        LambdaQueryWrapper<Product> wrapper = Wrappers.<Product>lambdaQuery()
                .select(Product::getId, Product::getName, Product::getSubtitle, Product::getCategoryId,
                        Product::getPrice, Product::getImageUrl, Product::getCreatedAt)
                .eq(Product::getStatus, 1);
        if ("price".equals(query.getSortBy())) {
            wrapper.eq(Product::getCategoryId, query.getCategoryId());
            if (position != null) {
                wrapper.and(boundary -> boundary.gt(Product::getPrice, position.lastPrice())
                        .or(tie -> tie.eq(Product::getPrice, position.lastPrice())
                                .lt(Product::getId, position.lastId())));
            }
            wrapper.orderByAsc(Product::getPrice).orderByDesc(Product::getId);
        } else {
            if (position != null) {
                wrapper.and(boundary -> boundary.lt(Product::getCreatedAt, position.lastCreatedAt())
                        .or(tie -> tie.eq(Product::getCreatedAt, position.lastCreatedAt())
                                .lt(Product::getId, position.lastId())));
            }
            wrapper.orderByDesc(Product::getCreatedAt, Product::getId);
        }

        // 分页插件只生成 LIMIT；多取一条判断后续批次，既不 COUNT 也不叠加手写 LIMIT。
        Page<Product> page = new Page<>(1L, query.getSize() + 1L, false);
        List<Product> fetched = productMapper.selectPage(page, wrapper).getRecords();
        boolean hasMore = fetched.size() > query.getSize();
        List<Product> products = fetched.subList(0, Math.min(query.getSize(), fetched.size()));
        if (products.isEmpty()) return new CursorPage<>(List.of(), null, false);

        Set<Long> categoryIds = products.stream().map(Product::getCategoryId).collect(Collectors.toSet());
        Map<Long, String> categoryNames = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                        .select(Category::getId, Category::getName).in(Category::getId, categoryIds))
                .stream().collect(Collectors.toMap(Category::getId, Category::getName));
        List<ProductCardVO> records = products.stream().map(product -> new ProductCardVO(
                product.getId().toString(), product.getName(), product.getSubtitle(),
                product.getCategoryId().toString(), categoryNames.getOrDefault(product.getCategoryId(), "分类不存在"),
                product.getPrice().setScale(2).toPlainString(), product.getImageUrl())).toList();
        Product last = products.getLast();
        String nextCursor = hasMore ? cursorCodec.encode(query,
                new Position(last.getId(), last.getPrice(), last.getCreatedAt())) : null;
        return new CursorPage<>(records, nextCursor, hasMore);
    }
}
