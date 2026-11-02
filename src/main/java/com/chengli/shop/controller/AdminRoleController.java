package com.chengli.shop.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chengli.shop.common.ApiResponse;
import com.chengli.shop.entity.Role;
import com.chengli.shop.mapper.RoleMapper;
import com.chengli.shop.service.account.AccountViews;
import com.chengli.shop.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 固定角色字典只读接口：只返回 ADMIN/USER，不支持新增或编辑角色码。 */
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final RoleMapper roleMapper;

    @GetMapping
    public ApiResponse<List<RoleVO>> list() {
        List<RoleVO> roles = this.roleMapper.selectList(Wrappers.<Role>lambdaQuery()
                        .in(Role::getCode, List.of("ADMIN", "USER")).orderByAsc(Role::getId))
                .stream().map(AccountViews::toRoleVO).toList();
        return ApiResponse.success(roles);
    }
}
