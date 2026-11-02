package com.chengli.shop.service.order;
import java.util.List;
public record NormalizedOrderCommand(String idempotencyKey, List<Item> items, String requestHash) {
    public NormalizedOrderCommand { items = List.copyOf(items); }
    public record Item(Long productId, int quantity) {}
}
