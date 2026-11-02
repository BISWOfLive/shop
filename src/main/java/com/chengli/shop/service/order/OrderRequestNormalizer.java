package com.chengli.shop.service.order;

import com.chengli.shop.dto.OrderCreateRequest;
import com.chengli.shop.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@Component
public class OrderRequestNormalizer {
    public NormalizedOrderCommand normalize(OrderCreateRequest request) {
        if (request == null || request.getIdempotencyKey() == null ||
                !request.getIdempotencyKey().matches("[\\x21-\\x7E]{1,64}")) throw invalid("下单请求号格式不正确");
        if (request.getItems() == null || request.getItems().isEmpty() || request.getItems().size() > 100)
            throw invalid("订单原始商品项必须为 1～100 条");
        var merged = new TreeMap<Long, Integer>();
        for (var item : request.getItems()) {
            if (item == null || item.getProductId() == null || item.getProductId() <= 0 ||
                    item.getQuantity() == null || item.getQuantity() < 1 || item.getQuantity() > 9999)
                throw invalid("商品 ID 或购买数量不正确");
            long quantity = (long) merged.getOrDefault(item.getProductId(), 0) + item.getQuantity();
            if (quantity > 9999) throw invalid("同种商品合并数量不能超过 9999");
            merged.put(item.getProductId(), (int) quantity);
        }
        if (merged.size() > 20) throw invalid("每单最多 20 种商品");
        var items = new ArrayList<NormalizedOrderCommand.Item>();
        var canonical = new StringBuilder();
        merged.forEach((id, quantity) -> {
            items.add(new NormalizedOrderCommand.Item(id, quantity));
            canonical.append(id).append(':').append(quantity).append('\n');
        });
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
            return new NormalizedOrderCommand(request.getIdempotencyKey(), items, hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
    private BusinessException invalid(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, message); }
}
