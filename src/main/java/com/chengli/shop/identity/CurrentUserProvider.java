package com.chengli.shop.identity;

/** 当前请求的真实身份入口；缺失身份返回 401，角色不足返回 403。 */
public interface CurrentUserProvider {
    Long requireUserId();

    Long requireAdminId();
}
