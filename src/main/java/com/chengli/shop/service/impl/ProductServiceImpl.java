package com.chengli.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengli.shop.common.PageResult;
import com.chengli.shop.dto.ProductQuery;
import com.chengli.shop.dto.ProductSaveRequest;
import com.chengli.shop.entity.Category;
import com.chengli.shop.entity.Product;
import com.chengli.shop.exception.BusinessException;
import com.chengli.shop.mapper.CategoryMapper;
import com.chengli.shop.mapper.ProductMapper;
import com.chengli.shop.service.ProductService;
import com.chengli.shop.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResult<ProductVO> page(ProductQuery query) {
        Page<Product> page = new Page<>(query.getPage(), query.getSize());
        productMapper.selectPage(page, buildQuery(query));
        List<ProductVO> records = toProductVOs(page.getRecords());
        return new PageResult<>(records, page.getTotal(), query.getPage(), query.getSize());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductVO getById(Long id) {
        Product product = requireProduct(id);
        Category category = categoryMapper.selectById(product.getCategoryId());
        return toVO(product, category == null ? "分类不存在" : category.getName());
    }

    @Override
    @Transactional
    public ProductVO create(ProductSaveRequest request) {
        Category category = requireCategory(request.getCategoryId());
        Product product = toEntity(request);
        productMapper.insert(product);
        // 读取数据库实际保存的时间精度，再组装响应。
        return toVO(requireProduct(product.getId()), category.getName());
    }

    @Override
    @Transactional
    public ProductVO update(Long id, ProductSaveRequest request) {
        requireProduct(id);
        Category category = requireCategory(request.getCategoryId());
        Product product = toEntity(request);
        product.setId(id);
        if (productMapper.updateById(product) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return toVO(requireProduct(id), category.getName());
    }

    private LambdaQueryWrapper<Product> buildQuery(ProductQuery query) {
        LambdaQueryWrapper<Product> wrapper = Wrappers.lambdaQuery();
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            String keyword = query.getKeyword().strip()
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_");
            // 固定 LIKE 表达式，用户输入通过参数绑定，保留通配符字面匹配。
            wrapper.apply("name LIKE {0} ESCAPE '!'", "%" + keyword + "%");
        }
        wrapper.eq(query.getCategoryId() != null, Product::getCategoryId, query.getCategoryId())
                .eq(query.getStatus() != null, Product::getStatus, query.getStatus());

        boolean ascending = "asc".equals(query.getSortOrder());
        switch (query.getSortBy()) {
            case "price" -> wrapper.orderBy(true, ascending, Product::getPrice);
            case "name" -> wrapper.orderBy(true, ascending, Product::getName);
            case "createdAt" -> wrapper.orderBy(true, ascending, Product::getCreatedAt);
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "不支持的商品排序字段");
        }
        return wrapper.orderByDesc(Product::getId);
    }

    private List<ProductVO> toProductVOs(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        Set<Long> categoryIds = products.stream()
                .map(Product::getCategoryId)
                .collect(Collectors.toSet());
        // 分类按当前页的 ID 集合一次查询，不在商品循环中访问数据库。
        Map<Long, String> categoryNames = categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                        .select(Category::getId, Category::getName)
                        .in(Category::getId, categoryIds))
                .stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        return products.stream()
                .map(product -> toVO(product, categoryNames.getOrDefault(product.getCategoryId(), "分类不存在")))
                .toList();
    }

    private Product requireProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        return product;
    }

    private Category requireCategory(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "所选分类不存在");
        }
        return category;
    }

    private Product toEntity(ProductSaveRequest request) {
        Product product = new Product();
        product.setName(request.getName().strip());
        product.setSubtitle(request.getSubtitle() == null ? "" : request.getSubtitle().strip());
        product.setCategoryId(request.getCategoryId());
        product.setPrice(request.getPrice());
        product.setDescription(request.getDescription() == null ? "" : request.getDescription().strip());
        product.setImageUrl(request.getImageUrl() == null ? "" : request.getImageUrl().strip());
        product.setStatus(request.getStatus());
        return product;
    }

    private ProductVO toVO(Product product, String categoryName) {
        ProductVO vo = new ProductVO();
        vo.setId(product.getId());
        vo.setName(product.getName());
        vo.setSubtitle(product.getSubtitle());
        vo.setCategoryId(product.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setPrice(product.getPrice());
        vo.setDescription(product.getDescription());
        vo.setImageUrl(product.getImageUrl());
        vo.setStatus(product.getStatus());
        vo.setCreatedAt(product.getCreatedAt());
        vo.setUpdatedAt(product.getUpdatedAt());
        return vo;
    }
}
