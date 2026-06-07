/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import com.baomidou.mybatisplus.annotation.FieldStrategy;

public record ColumnMeta(
    String columnName,
    String fieldName,
    Class<?> type,
    String columnTypeOverride,
    boolean isPrimaryKey,
    FieldStrategy insertStrategy
) {
}
