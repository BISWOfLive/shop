package com.chengli.shop.controller;

import com.chengli.shop.common.ApiResponse;
import com.chengli.shop.dto.UserLoginRequest;
import com.chengli.shop.dto.UserRegisterRequest;
import com.chengli.shop.service.account.AccountRegistrationService;
import com.chengli.shop.service.account.AuthenticationService;
import com.chengli.shop.vo.CsrfTokenVO;
import com.chengli.shop.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录、注册、退出与当前身份契约；所有账户响应不进入公共或浏览器业务缓存。 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AccountRegistrationService registrationService;
    private final AuthenticationService authenticationService;

    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<CsrfTokenVO>> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(new CsrfTokenVO(csrfToken.getHeaderName(), csrfToken.getToken())));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserVO>> register(@Valid @RequestBody UserRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(this.registrationService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserVO>> login(@Valid @RequestBody UserLoginRequest request,
                                                     HttpServletRequest httpRequest,
                                                     HttpServletResponse httpResponse) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(this.authenticationService.login(request, httpRequest, httpResponse)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest,
                                                    HttpServletResponse httpResponse) {
        this.authenticationService.logout(httpRequest, httpResponse);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserVO>> me() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(this.authenticationService.currentUser()));
    }
}
