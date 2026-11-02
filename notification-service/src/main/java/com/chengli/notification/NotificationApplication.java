package com.chengli.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 站内通知独立进程：只拥有 test_shop_notification 库，不依赖商城工程、订单库、商品缓存或浏览器 Session。
 */
@SpringBootApplication
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
