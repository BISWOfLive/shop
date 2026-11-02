package com.chengli.shop.controller;

import com.chengli.shop.common.ApiResponse;
import com.chengli.shop.dto.UserProfileUpdateRequest;
import com.chengli.shop.service.account.AuthenticationService;
import com.chengli.shop.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本人资料维护；用户 ID 只来自身份上下文，不接受路径或请求体指定。 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final AuthenticationService authenticationService;

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserVO>> update(@Valid @RequestBody UserProfileUpdateRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(this.authenticationService.updateProfile(request)));
    }
}
