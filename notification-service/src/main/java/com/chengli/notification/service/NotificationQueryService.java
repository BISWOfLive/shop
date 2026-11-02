package com.chengli.notification.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chengli.notification.common.PageResult;
import com.chengli.notification.dto.NotificationQuery;
import com.chengli.notification.entity.Notification;
import com.chengli.notification.mapper.NotificationMapper;
import com.chengli.notification.vo.NotificationVO;
import com.chengli.notification.vo.UnreadNotificationCountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 本人通知查询与首次已读。所有 SQL 都带当前接收人条件；
 * 查无此通知或不属于本人统一 404，不先按 ID 查询再暴露归属。
 */
@Service
@RequiredArgsConstructor
public class NotificationQueryService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final NotificationMapper notificationMapper;

    @Transactional(readOnly = true)
    public PageResult<NotificationVO> page(Long recipientId, NotificationQuery query) {
        var conditions = Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getRecipientUserId, recipientId);
        if (Boolean.TRUE.equals(query.getUnread())) {
            conditions.isNull(Notification::getReadAt);
        } else if (Boolean.FALSE.equals(query.getUnread())) {
            conditions.isNotNull(Notification::getReadAt);
        }
        if (query.getOrderNo() != null && !query.getOrderNo().isBlank()) {
            conditions.eq(Notification::getOrderNo, query.getOrderNo());
        }
        // 通知可能乱序到达，列表按落库时间与 ID 降序，不承诺订单事件全局时间线。
        conditions.orderByDesc(Notification::getCreatedAt).orderByDesc(Notification::getId);
        Page<Notification> result = notificationMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), conditions);
        List<NotificationVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(records, result.getTotal(), query.getPage(), query.getSize());
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountVO unreadCount(Long recipientId) {
        long count = notificationMapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getRecipientUserId, recipientId)
                .isNull(Notification::getReadAt));
        return new UnreadNotificationCountVO(Long.toString(Math.max(0, count)));
    }

    @Transactional
    public NotificationVO markRead(Long recipientId, String notificationId) {
        Long id = parseNotificationId(notificationId);
        // 条件首次更新：只有本人且仍未读才写入 read_at；重复已读保留第一次时间。
        int updated = notificationMapper.update(null, Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getId, id)
                .eq(Notification::getRecipientUserId, recipientId)
                .isNull(Notification::getReadAt)
                .setSql("read_at = CURRENT_TIMESTAMP(3)"));
        // UPDATE=0 不能直接返回成功：必须区分已读与不属于本人 / 不存在。
        var notification = notificationMapper.selectOne(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getId, id)
                .eq(Notification::getRecipientUserId, recipientId));
        if (notification == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "通知不存在");
        }
        if (updated > 1) {
            throw new IllegalStateException("通知已读更新影响了多行");
        }
        return toVO(notification);
    }

    private Long parseNotificationId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}") || Long.parseLong(value) <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "通知不存在");
        }
        return Long.valueOf(value);
    }

    private NotificationVO toVO(Notification notification) {
        return new NotificationVO(notification.getId().toString(), notification.getOrderNo(),
                notification.getTitle(), notification.getContent(),
                toResponseTime(notification.getReadAt()), toResponseTime(notification.getCreatedAt()));
    }

    private OffsetDateTime toResponseTime(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }
}
