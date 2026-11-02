package com.chengli.shop.controller;

import com.chengli.shop.common.ApiResponse;
import com.chengli.shop.common.PageResult;
import com.chengli.shop.dto.UserQuery;
import com.chengli.shop.dto.UserRoleSaveRequest;
import com.chengli.shop.dto.UserStatusUpdateRequest;
import com.chengli.shop.service.account.AccountAdministrationService;
import com.chengli.shop.vo.UserVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理员账号列表、启用状态与固定角色维护；权限由安全配置统一要求 ADMIN。 */
@Validated
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AccountAdministrationService administrationService;

    @GetMapping
    public ApiResponse<PageResult<UserVO>> page(@Valid @ModelAttribute UserQuery query) {
        return ApiResponse.success(this.administrationService.page(query));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<UserVO> changeStatus(
            @PathVariable("id") @Positive(message = "用户 ID 必须为正整数") Long id,
            @Valid @RequestBody UserStatusUpdateRequest request) {
        return ApiResponse.success(this.administrationService.changeStatus(id, request));
    }

    @PutMapping("/{id}/roles")
    public ApiResponse<UserVO> replaceRoles(
            @PathVariable("id") @Positive(message = "用户 ID 必须为正整数") Long id,
            @Valid @RequestBody UserRoleSaveRequest request) {
        return ApiResponse.success(this.administrationService.replaceRoles(id, request));
    }
}
