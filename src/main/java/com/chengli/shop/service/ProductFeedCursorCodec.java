package com.chengli.shop.service;

import com.chengli.shop.dto.CursorProductQuery;
import com.chengli.shop.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Base64;

@Component
public class ProductFeedCursorCodec {
    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS").withResolverStyle(ResolverStyle.STRICT);

    public record Position(Long lastId, BigDecimal lastPrice, LocalDateTime lastCreatedAt) {
    }

    public Position decode(CursorProductQuery query) {
        String cursor = query.getCursor();
        if (cursor == null) return null;
        try {
            if (cursor.length() > 512 || !cursor.matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException();
            }
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(cursor)) {
                throw new IllegalArgumentException();
            }
            String[] parts = new String(decoded, StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 5 || !"v2".equals(parts[0]) || !query.getSortBy().equals(parts[1])
                    || !scope(query).equals(parts[2]) || !parts[4].matches("[1-9][0-9]{0,18}")) {
                throw new IllegalArgumentException();
            }
            long id = Long.parseLong(parts[4]);
            if ("price".equals(query.getSortBy())) {
                if (!parts[3].matches("(?:0|[1-9][0-9]{0,9})\\.[0-9]{2}")) {
                    throw new IllegalArgumentException();
                }
                BigDecimal price = new BigDecimal(parts[3]);
                if (price.signum() <= 0) throw new IllegalArgumentException();
                return new Position(id, price, null);
            }
            LocalDateTime time = LocalDateTime.parse(parts[3], TIME);
            if (time.getYear() < 1000 || time.getYear() > 9999 || !TIME.format(time).equals(parts[3])) {
                throw new IllegalArgumentException();
            }
            return new Position(id, null, time);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "商品游标无效或与当前分类、排序不匹配，请重新加载商品");
        }
    }

    public String encode(CursorProductQuery query, Position position) {
        String boundary = "price".equals(query.getSortBy())
                ? position.lastPrice().setScale(2).toPlainString() : TIME.format(position.lastCreatedAt());
        String payload = String.join("|", "v2", query.getSortBy(), scope(query), boundary,
                position.lastId().toString());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String scope(CursorProductQuery query) {
        return query.getCategoryId() == null ? "all" : query.getCategoryId().toString();
    }
}
