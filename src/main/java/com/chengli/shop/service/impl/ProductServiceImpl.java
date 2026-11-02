package com.chengli.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengli.shop.common.PageResult;
import com.chengli.shop.dto.ProductQuery;
import com.chengli.shop.dto.ProductSaveRequest;
import com.chengli.shop.dto.ProductImageSaveRequest;
import com.chengli.shop.entity.Category;
import com.chengli.shop.entity.Product;
import com.chengli.shop.entity.Inventory;
import com.chengli.shop.entity.ProductImage;
import com.chengli.shop.enums.ProductImageType;
import com.chengli.shop.exception.BusinessException;
import com.chengli.shop.mapper.CategoryMapper;
import com.chengli.shop.mapper.ProductMapper;
import com.chengli.shop.mapper.InventoryMapper;
import com.chengli.shop.mapper.ProductImageMapper;
import com.chengli.shop.service.ProductService;
import com.chengli.shop.service.cache.ProductChangedEvent;
import com.chengli.shop.vo.ProductVO;
import com.chengli.shop.vo.ProductDetailVO;
import com.chengli.shop.vo.ProductImageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final InventoryMapper inventoryMapper;
    private final ProductImageMapper productImageMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public PageResult<ProductVO> pagePublic(ProductQuery query) {
        if (query.getStatus() != null && !Integer.valueOf(1).equals(query.getStatus())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "公开商品查询只能浏览上架商品");
        }
        // 公开入口不接受客户端扩大状态范围，统一按上架筛选。
        query.setStatus(1);
        return pageInternal(query);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<ProductVO> pageAdmin(ProductQuery query) {
        return pageInternal(query);
    }

    private PageResult<ProductVO> pageInternal(ProductQuery query) {
        Page<Product> page = new Page<>(query.getPage(), query.getSize(), query.getIncludeTotal());
        productMapper.selectPage(page, buildQuery(query));
        List<ProductVO> records = toProductVOs(page.getRecords());
        return new PageResult<>(records, query.getIncludeTotal() ? page.getTotal() : -1L,
                query.getPage(), query.getSize());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductVO getById(Long id) {
        Product product = requireProduct(id);
        Category category = categoryMapper.selectById(product.getCategoryId());
        return toVO(product, category == null ? "分类不存在" : category.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDetailVO getDetail(Long id) {
        return loadDetail(id);
    }

    @Override
    @Transactional
    public ProductDetailVO create(ProductSaveRequest request) {
        validateImages(request);
        requireCategory(request.getCategoryId());
        Product product = toEntity(request);
        if (productMapper.insert(product) != 1) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "商品保存失败");
        }
        Inventory inventory = new Inventory();
        inventory.setProductId(product.getId());
        if (inventoryMapper.insert(inventory) != 1) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "商品库存初始化失败");
        }
        replaceImages(product.getId(), request.getImages());
        ProductDetailVO detail = loadDetail(product.getId());
        eventPublisher.publishEvent(new ProductChangedEvent(product.getId()));
        return detail;
    }

    @Override
    @Transactional
    public ProductDetailVO update(Long id, ProductSaveRequest request) {
        requireProduct(id);
        requireCategory(request.getCategoryId());
        validateImages(request);
        Product product = toEntity(request);
        product.setId(id);
        if (productMapper.updateById(product) == 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "商品不存在");
        }
        replaceImages(id, request.getImages());
        ProductDetailVO detail = loadDetail(id);
        eventPublisher.publishEvent(new ProductChangedEvent(id));
        return detail;
    }

    private void validateImages(ProductSaveRequest request) {
        List<ProductImageSaveRequest> images = request.getImages();
        if (images == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "请明确提交图片集合，无图请传空数组");
        }
        if (images.size() > 28) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "附图与详情图合计最多 28 张");
        }
        Set<String> carouselUrls = new HashSet<>();
        Set<String> detailUrls = new HashSet<>();
        String mainUrl = request.getImageUrl() == null ? "" : request.getImageUrl().strip();
        for (ProductImageSaveRequest image : images) {
            if (image == null || image.getImageType() == null || image.getImageUrl() == null
                    || image.getImageUrl().isBlank() || image.getSortOrder() == null || image.getSortOrder() < 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "图片信息不完整或排序不合法");
            }
            String url = image.getImageUrl().strip();
            if (image.getImageType() == ProductImageType.CAROUSEL.getCode()) {
                if (url.equals(mainUrl)) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "轮播附图不能重复主图");
                }
                if (!carouselUrls.add(url)) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "同组图片地址不能重复");
                }
            } else if (image.getImageType() == ProductImageType.DETAIL.getCode()) {
                if (!detailUrls.add(url)) {
                    throw new BusinessException(HttpStatus.BAD_REQUEST, "同组图片地址不能重复");
                }
            } else {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "图片类型只能为 1 或 2");
            }
        }
        if (carouselUrls.size() > 8 || detailUrls.size() > 20) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "轮播附图最多 8 张，详情图最多 20 张");
        }
    }

    private void replaceImages(Long productId, List<ProductImageSaveRequest> images) {
        productImageMapper.delete(Wrappers.<ProductImage>lambdaQuery().eq(ProductImage::getProductId, productId));
        for (ProductImageSaveRequest image : images) {
            ProductImage entity = new ProductImage();
            entity.setProductId(productId);
            entity.setImageType(image.getImageType());
            entity.setImageUrl(image.getImageUrl().strip());
            entity.setAltText(image.getAltText() == null ? "" : image.getAltText().strip());
            entity.setSortOrder(image.getSortOrder());
            if (productImageMapper.insert(entity) != 1) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "商品图片保存失败");
            }
        }
    }

    private ProductDetailVO loadDetail(Long id) {
        Product product = requireProduct(id);
        Category category = categoryMapper.selectById(product.getCategoryId());
        List<ProductImage> images = productImageMapper.selectList(Wrappers.<ProductImage>lambdaQuery()
                .eq(ProductImage::getProductId, id)
                .orderByAsc(ProductImage::getImageType, ProductImage::getSortOrder, ProductImage::getId));
        ProductDetailVO vo = new ProductDetailVO();
        fillProductVO(vo, product, category == null ? "分类不存在" : category.getName());
        vo.setCarouselImages(images.stream()
                .filter(image -> image.getImageType() == ProductImageType.CAROUSEL.getCode())
                .map(this::toImageVO).toList());
        vo.setDetailImages(images.stream()
                .filter(image -> image.getImageType() == ProductImageType.DETAIL.getCode())
                .map(this::toImageVO).toList());
        return vo;
    }

    private ProductImageVO toImageVO(ProductImage image) {
        ProductImageVO vo = new ProductImageVO();
        vo.setId(image.getId());
        vo.setProductId(image.getProductId());
        vo.setImageType(image.getImageType());
        vo.setImageUrl(image.getImageUrl());
        vo.setAltText(image.getAltText());
        vo.setSortOrder(image.getSortOrder());
        return vo;
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
        fillProductVO(vo, product, categoryName);
        return vo;
    }

    private void fillProductVO(ProductVO vo, Product product, String categoryName) {
        vo.setId(product.getId());
        vo.setName(product.getName());
        vo.setSubtitle(product.getSubtitle());
        vo.setCategoryId(product.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setPrice(product.getPrice().setScale(2));
        vo.setDescription(product.getDescription());
        vo.setImageUrl(product.getImageUrl());
        vo.setStatus(product.getStatus());
        vo.setCreatedAt(toResponseTime(product.getCreatedAt()));
        vo.setUpdatedAt(toResponseTime(product.getUpdatedAt()));
    }

    private OffsetDateTime toResponseTime(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }
}
