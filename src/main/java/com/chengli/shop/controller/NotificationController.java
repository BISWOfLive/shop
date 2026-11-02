package com.chengli.shop.controller;

import com.chengli.shop.common.ApiResponse;
import com.chengli.shop.common.PageResult;
import com.chengli.shop.config.NotificationProperties;
import com.chengli.shop.dto.NotificationQuery;
import com.chengli.shop.exception.BusinessException;
import com.chengli.shop.identity.CurrentUserProvider;
import com.chengli.shop.service.notification.NotificationClient;
import com.chengli.shop.vo.NotificationVO;
import com.chengli.shop.vo.UnreadNotificationCountVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 浏览器同源通知入口：保留 Session/CSRF，接收人只取当前登录身份，管理员也只能读本人通知。
 * 通知模块关闭时返回明确的 503 模块不可用，不返回空列表伪装正常；所有结果 no-store。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final CurrentUserProvider currentUser;
    private final NotificationProperties properties;
    private final ObjectProvider<NotificationClient> clientProvider;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResult<NotificationVO>>> page(
            @Valid @ModelAttribute NotificationQuery query) {
        var client = requireClient();
        return noStore(ApiResponse.success(client.page(currentUser.requireUserId(), query)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadNotificationCountVO>> unreadCount() {
        var client = requireClient();
        return noStore(ApiResponse.success(client.unreadCount(currentUser.requireUserId())));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationVO>> markRead(@PathVariable String id) {
        var client = requireClient();
        return noStore(ApiResponse.success(client.markRead(currentUser.requireUserId(), parseId(id))));
    }

    private NotificationClient requireClient() {
        if (!properties.isEnabled()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "通知模块不可用");
        }
        NotificationClient client = clientProvider.getIfAvailable();
        if (client == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "通知模块不可用");
        }
        return client;
    }

    private Long parseId(String value) {
        // 路径参数不是合法正十进制 ID 时按“查无此通知”处理，不泄漏内部实现细节。
        if (value == null || !value.matches("[1-9][0-9]{0,18}") || Long.parseLong(value) <= 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "通知不存在");
        }
        return Long.valueOf(value);
    }

    private <T> ResponseEntity<ApiResponse<T>> noStore(ApiResponse<T> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
