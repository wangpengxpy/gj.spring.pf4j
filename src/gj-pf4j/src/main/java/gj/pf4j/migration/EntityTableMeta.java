/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import java.util.List;

public record EntityTableMeta(
    String tableName,
    String className,
    String primaryKeyColumn,
    String primaryKeyJavaType,
    PrimaryKeyStrategy primaryKeyStrategy,
    List<ColumnMeta> columns
) {

    public enum PrimaryKeyStrategy {
        AUTO,
        INPUT,
        NONE
    }
}
