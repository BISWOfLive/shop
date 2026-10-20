package com.chengli.shop.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/** 用户响应；不包含明文密码和 passwordHash。 */
@Data
public class UserVO {
    private Long id;
    private String username;
    private String nickname;
    private Integer status;
    private List<RoleVO> roles = List.of();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
