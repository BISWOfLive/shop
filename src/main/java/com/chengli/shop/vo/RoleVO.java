package com.chengli.shop.vo;

import lombok.Data;

/** 角色字典响应；外部 ID 使用十进制字符串。 */
@Data
public class RoleVO {
    private String id;
    private String code;
    private String name;
}
