package com.chengli.shop;

import com.chengli.shop.dto.ProductQuery;
import com.chengli.shop.dto.ProductSaveRequest;
import com.chengli.shop.exception.BusinessException;
import com.chengli.shop.mapper.CategoryMapper;
import com.chengli.shop.mapper.ProductMapper;
import com.chengli.shop.service.ProductService;
import com.chengli.shop.vo.ProductVO;
import com.jayway.jsonpath.JsonPath;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用当前 MySQL 配置验证迁移；各用例的数据由测试事务回滚。
 */
@Transactional
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MybatisPlusMigrationTests {

    @Autowired
    private ProductService productService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SqlSessionFactory sqlSessionFactory;
    @Value("${local.server.port}")
    private int port;

    private Long categoryId;
    private String prefix;

    @BeforeEach
    void prepareCategory() {
        prefix = "mp-" + UUID.randomUUID();
        String name = prefix + "-category";
        jdbc.update("INSERT INTO category (name, sort_order) VALUES (?, ?)", name, 0);
        categoryId = jdbc.queryForObject("SELECT id FROM category WHERE name = ?", Long.class, name);
    }

    @Test
    void usesMybatisPlusSqlSessionAndBaseMappers() {
        assertEquals("com.baomidou.mybatisplus.core.MybatisConfiguration",
                sqlSessionFactory.getConfiguration().getClass().getName());
        assertTrue(extendsBaseMapper(ProductMapper.class));
        assertTrue(extendsBaseMapper(CategoryMapper.class));
    }

    @Test
    void createsAndUpdatesProductIncludingClearingOptionalFields() {
        ProductSaveRequest request = request(prefix + "-product", "39.90", 1);
        request.setDescription("需要清除的描述");
        request.setImageUrl("https://example.com/product.png");
        ProductVO created = productService.create(request);

        assertNotNull(created.getId());
        assertEquals(0, jdbc.queryForObject("SELECT available_qty FROM inventory WHERE product_id = ?",
                Integer.class, created.getId()));
        assertTrue(created.getId() > 0);
        assertEquals(categoryId, created.getCategoryId());
        assertNotNull(created.getCategoryName());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());
        assertEquals(0, new BigDecimal("39.90").compareTo(created.getPrice()));

        ProductSaveRequest update = request(prefix + "-updated", "59.80", 0);
        ProductVO updated = productService.update(created.getId(), update);
        assertEquals(created.getId(), updated.getId());
        assertEquals(update.getName(), updated.getName());
        assertEquals(0, updated.getStatus());
        assertEquals("", updated.getDescription());
        assertEquals("", updated.getImageUrl());
        assertEquals(created.getCreatedAt(), updated.getCreatedAt());
        assertFalse(updated.getUpdatedAt().isBefore(created.getUpdatedAt()));
        assertEquals(0, new BigDecimal("59.80").compareTo(updated.getPrice()));
    }

    @Test
    void paginatesWithTotalAndCategoryStatusPriceFilters() {
        ProductVO expensive = productService.create(request(prefix + "-high", "29.90", 1));
        ProductVO cheap = productService.create(request(prefix + "-low", "12.50", 0));

        ProductQuery query = query();
        query.setSize(1);
        query.setSortBy("price");
        query.setSortOrder("asc");
        var first = productService.page(query);
        assertEquals(2L, first.total());
        assertEquals(1, first.records().size());
        assertEquals(cheap.getId(), first.records().getFirst().getId());

        query.setPage(2);
        var second = productService.page(query);
        assertEquals(2L, second.total());
        assertEquals(expensive.getId(), second.records().getFirst().getId());

        query.setPage(1);
        query.setStatus(1);
        var active = productService.page(query);
        assertEquals(1L, active.total());
        assertEquals(expensive.getId(), active.records().getFirst().getId());

        query.setPage(100);
        var beyondEnd = productService.page(query);
        assertEquals(1L, beyondEnd.total());
        assertTrue(beyondEnd.records().isEmpty());
    }

    @Test
    void treatsLikeWildcardsAsLiteralCharacters() {
        ProductVO literal = productService.create(request(prefix + "-literal%_!", "10.00", 1));
        productService.create(request(prefix + "-literalABC", "20.00", 1));
        ProductQuery query = query();
        query.setKeyword(prefix + "-literal%_!");
        var result = productService.page(query);
        assertEquals(1L, result.total());
        assertEquals(literal.getId(), result.records().getFirst().getId());
    }

    @Test
    void keepsIdTieBreakerAndUsesConfiguredNameSorting() {
        ProductVO alpha = productService.create(request(prefix + "-alpha", "10.00", 1));
        ProductVO beta = productService.create(request(prefix + "-beta", "10.00", 1));
        jdbc.update("UPDATE product SET created_at = '2026-01-01 00:00:00' WHERE category_id = ?", categoryId);

        ProductQuery query = query();
        query.setSize(1);
        assertEquals(beta.getId(), productService.page(query).records().getFirst().getId());
        query.setPage(2);
        assertEquals(alpha.getId(), productService.page(query).records().getFirst().getId());

        query.setPage(1);
        query.setSortBy("name");
        query.setSortOrder("asc");
        assertEquals(alpha.getId(), productService.page(query).records().getFirst().getId());
    }

    @Test
    void preservesMissingProductAndCategoryErrors() {
        var missingProduct = assertThrows(BusinessException.class, () -> productService.getById(Long.MAX_VALUE));
        assertEquals(HttpStatus.NOT_FOUND, missingProduct.getStatus());

        ProductSaveRequest invalidCategory = request(prefix + "-invalid", "10.00", 1);
        invalidCategory.setCategoryId(Long.MAX_VALUE);
        var missingCategory = assertThrows(BusinessException.class, () -> productService.create(invalidCategory));
        assertEquals(HttpStatus.BAD_REQUEST, missingCategory.getStatus());
    }

    @Test
    void servesExistingApiContractAndRejectsInvalidSorting() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> categories = client.send(
                httpRequest("/api/categories"), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, categories.statusCode(), categories.body());
        assertEquals(0, JsonPath.parse(categories.body()).read("$.code", Integer.class));
        assertNotNull(JsonPath.parse(categories.body()).read("$.data"));

        HttpResponse<String> page = client.send(
                httpRequest("/api/products?page=1&size=1&sortBy=price&sortOrder=asc"),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode(), page.body());
        assertEquals(0, JsonPath.parse(page.body()).read("$.code", Integer.class));
        assertNotNull(JsonPath.parse(page.body()).read("$.data.records"));
        assertNotNull(JsonPath.parse(page.body()).read("$.data.total"));

        HttpResponse<String> invalid = client.send(
                httpRequest("/api/products?sortBy=unknown"),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, invalid.statusCode(), invalid.body());
    }

    private boolean extendsBaseMapper(Class<?> mapper) {
        return Arrays.stream(mapper.getInterfaces())
                .anyMatch(parent -> parent.getName().equals("com.baomidou.mybatisplus.core.mapper.BaseMapper"));
    }

    private ProductSaveRequest request(String name, String price, int status) {
        ProductSaveRequest request = new ProductSaveRequest();
        request.setName(name);
        request.setCategoryId(categoryId);
        request.setPrice(new BigDecimal(price));
        request.setStatus(status);
        if (status == 1) {
            request.setImageUrl("https://example.com/migration-product.png");
        }
        return request;
    }

    private ProductQuery query() {
        ProductQuery query = new ProductQuery();
        query.setCategoryId(categoryId);
        query.setKeyword(prefix);
        return query;
    }

    private HttpRequest httpRequest(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).GET().build();
    }
}
