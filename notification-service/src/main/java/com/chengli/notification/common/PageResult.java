package com.chengli.notification.common;

import java.util.List;

/** 服务间分页结构；与商城 PageResult 保持相同字段名，但不共享代码。 */
public record PageResult<T>(List<T> records, long total, int page, int size) {
}
