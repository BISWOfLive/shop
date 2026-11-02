package com.chengli.notification.controller;

import com.chengli.notification.common.ApiResponse;
import com.chengli.notification.common.PageResult;
import com.chengli.notification.dto.NotificationQuery;
import com.chengli.notification.identity.CurrentRecipientProvider;
import com.chengli.notification.service.NotificationQueryService;
import com.chengli.notification.vo.NotificationVO;
import com.chengli.notification.vo.UnreadNotificationCountVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部通知接口：只接受内部签名凭据，接收人只取凭据 sub。
 * 浏览器不直接访问本端口；商城的 /api/notifications 是同源代理。
 */
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationQueryService service;
    private final CurrentRecipientProvider recipients;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResult<NotificationVO>>> page(
            @Valid @ModelAttribute NotificationQuery query) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.page(recipients.requireRecipientId(), query)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadNotificationCountVO>> unreadCount() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.unreadCount(recipients.requireRecipientId())));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationVO>> markRead(@PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.markRead(recipients.requireRecipientId(), id)));
    }
}
