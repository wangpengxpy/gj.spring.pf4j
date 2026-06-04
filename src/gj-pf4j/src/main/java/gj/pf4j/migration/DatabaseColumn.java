/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class DatabaseColumn {

    private final String name;
    private String storeType;
    private final boolean nullable;
    private Object defaultValue;
    private String defaultValueSql;
    private String collation;
    private String comment;

    public DatabaseColumn(String name, String storeType, boolean nullable) {
        this.name = name;
        this.storeType = storeType;
        this.nullable = nullable;
    }

    public DatabaseColumn(String name, int jdbcDataType, int columnSize, boolean nullable) {
        this.name = name;
        this.jdbcDataType = jdbcDataType;
        this.columnSize = columnSize;
        this.nullable = nullable;
    }

    @lombok.Setter(lombok.AccessLevel.NONE)
    private int jdbcDataType;
    @lombok.Setter(lombok.AccessLevel.NONE)
    private int columnSize;
}
