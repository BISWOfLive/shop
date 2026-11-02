package com.chengli.shop.vo;

/** 前端只在内存保存 CSRF 头名与令牌；令牌不写入 localStorage。 */
public record CsrfTokenVO(String headerName, String token) {
}
