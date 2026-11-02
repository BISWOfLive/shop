package com.chengli.shop.vo;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/** 用户响应；不包含明文密码和 passwordHash，外部 ID 使用十进制字符串。 */
@Data
public class UserVO {
    private String id;
    private String username;
    private String nickname;
    private Integer status;
    private List<RoleVO> roles = List.of();
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    /** 前端可见的功能能力，不代表请求必然成功；后端仍授权每次调用。 */
    private Features features = new Features();

    @Data
    public static class Features {
        private boolean notifications;
    }
}
