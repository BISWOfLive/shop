package com.chengli.shop.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chengli.shop.entity.Category;
import com.chengli.shop.mapper.CategoryMapper;
import com.chengli.shop.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    @Override
    public List<Category> list() {
        return categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId));
    }
}
