package com.chengli.shop.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 第一名管理员命令入口的参数。默认关闭；用户名、密码和昵称只从环境变量读取，
 * 命令行、日志与文档都不保存实际口令。
 */
@Data
@Component
@ConfigurationProperties(prefix = "shop.bootstrap-admin")
public class BootstrapAdminProperties {
    private boolean enabled = false;
    private String username = "";
    private String password = "";
    private String nickname = "";
}
