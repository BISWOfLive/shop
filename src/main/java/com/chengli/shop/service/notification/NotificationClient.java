package com.chengli.shop.service.notification;

import com.chengli.shop.common.PageResult;
import com.chengli.shop.config.NotificationProperties;
import com.chengli.shop.dto.NotificationQuery;
import com.chengli.shop.exception.BusinessException;
import com.chengli.shop.security.NotificationTokenIssuer;
import com.chengli.shop.vo.NotificationVO;
import com.chengli.shop.vo.UnreadNotificationCountVO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 商城到通知服务的受限内部客户端：按读/写接口选择 scope，连接 500ms、单次响应 2s，
 * 响应体读取上限 64 KiB，不自动重放写操作。通知服务不可用统一映射为 503，
 * 查无归属映射为 404；内部错误堆栈和凭据不转发给浏览器。
 */
@Service
@ConditionalOnProperty(prefix = "shop.notification", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class NotificationClient {
    private final RestClient notificationRestClient;
    private final NotificationTokenIssuer tokenIssuer;
    private final NotificationProperties properties;
    private final JsonMapper jsonMapper;

    public PageResult<NotificationVO> page(Long userId, NotificationQuery query) {
        Map<?, ?> data = data(userId, NotificationTokenIssuer.SCOPE_READ,
                "/internal/notifications?" + pageQuery(query), HttpMethod.GET);
        var records = new ArrayList<NotificationVO>();
        if (data.get("records") instanceof List<?> rows) {
            for (Object row : rows) {
                if (row instanceof Map<?, ?> value) records.add(toVO(value));
            }
        }
        return new PageResult<>(records, number(data, "total").longValue(),
                number(data, "page").intValue(), number(data, "size").intValue());
    }

    public UnreadNotificationCountVO unreadCount(Long userId) {
        Map<?, ?> data = data(userId, NotificationTokenIssuer.SCOPE_READ,
                "/internal/notifications/unread-count", HttpMethod.GET);
        return new UnreadNotificationCountVO(text(data, "count"));
    }

    public NotificationVO markRead(Long userId, Long notificationId) {
        if (notificationId == null || notificationId <= 0) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "通知不存在");
        }
        Map<?, ?> data = data(userId, NotificationTokenIssuer.SCOPE_WRITE,
                "/internal/notifications/" + notificationId + "/read", HttpMethod.POST);
        return toVO(data);
    }

    private String pageQuery(NotificationQuery query) {
        var params = new StringBuilder("page=").append(query.getPage())
                .append("&size=").append(query.getSize());
        if (query.getUnread() != null) {
            params.append("&unread=").append(query.getUnread());
        }
        if (query.getOrderNo() != null && !query.getOrderNo().isBlank()) {
            params.append("&orderNo=").append(URLEncoder.encode(query.getOrderNo(), StandardCharsets.UTF_8));
        }
        return params.toString();
    }

    private Map<?, ?> data(Long userId, String scope, String path, HttpMethod method) {
        String token = tokenIssuer.issue(userId, scope);
        try {
            Map<?, ?> payload = notificationRestClient.method(method)
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .exchange((request, response) -> parsePayload(response));
            if (payload == null) throw unavailable();
            return payload;
        } catch (ResourceAccessException timeout) {
            throw unavailable();
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private Map<?, ?> parsePayload(ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        byte[] bytes = readBounded(response.getBody());
        Map<?, ?> envelope = parseJson(bytes);
        int code = number(envelope, "code").intValue();
        if (status == HttpStatus.NOT_FOUND.value()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "通知不存在");
        }
        if (status != HttpStatus.OK.value() || code != 0) {
            // 401/403/502/500 等内部失败不区分细节，浏览器统一看到通知服务不可用。
            throw unavailable();
        }
        if (!(envelope.get("data") instanceof Map<?, ?> data)) throw unavailable();
        return data;
    }

    private byte[] readBounded(InputStream input) throws IOException {
        int limit = properties.getInternal().getMaxResponseBytes();
        if (input == null) throw unavailable();
        try (input) {
            var output = new ByteArrayOutputStream(Math.min(limit, 8192));
            byte[] chunk = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(chunk)) >= 0) {
                total += read;
                if (total > limit) throw unavailable();
                output.write(chunk, 0, read);
            }
            if (total == 0) throw unavailable();
            return output.toByteArray();
        }
    }

    private Map<?, ?> parseJson(byte[] bytes) {
        try {
            Map<?, ?> value = jsonMapper.readValue(bytes, Map.class);
            if (value == null) throw unavailable();
            return value;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private NotificationVO toVO(Map<?, ?> value) {
        var vo = new NotificationVO();
        vo.setId(text(value, "id"));
        vo.setOrderNo(optionalText(value, "orderNo"));
        vo.setTitle(text(value, "title"));
        vo.setContent(text(value, "content"));
        vo.setReadAt(optionalTime(value, "readAt"));
        vo.setCreatedAt(requiredTime(value, "createdAt"));
        return vo;
    }

    private Number number(Map<?, ?> value, String field) {
        if (value.get(field) instanceof Number number) return number;
        throw unavailable();
    }

    private String text(Map<?, ?> value, String field) {
        if (value.get(field) instanceof String text) return text;
        throw unavailable();
    }

    private String optionalText(Map<?, ?> value, String field) {
        Object raw = value.get(field);
        if (raw == null) return null;
        if (raw instanceof String text) return text;
        throw unavailable();
    }

    private OffsetDateTime optionalTime(Map<?, ?> value, String field) {
        String raw = optionalText(value, field);
        try {
            return raw == null ? null : OffsetDateTime.parse(raw);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private OffsetDateTime requiredTime(Map<?, ?> value, String field) {
        OffsetDateTime value0 = optionalTime(value, field);
        if (value0 == null) throw unavailable();
        return value0;
    }

    private BusinessException unavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "通知服务暂不可用");
    }
}
