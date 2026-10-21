package com.chengli.shop.vo;

import java.util.List;

public record CursorPage<T>(List<T> records, String nextCursor, boolean hasMore) {
}
