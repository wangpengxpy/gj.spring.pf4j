/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import org.apache.ibatis.type.JdbcType;

public record ColumnMeta(
    String columnName,
    String fieldName,
    Class<?> type,
    String columnTypeOverride,
    JdbcType jdbcType,
    boolean isPrimaryKey,
    FieldStrategy insertStrategy
) {
}
