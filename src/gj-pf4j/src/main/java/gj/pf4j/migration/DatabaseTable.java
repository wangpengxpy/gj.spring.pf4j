/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

@Data
@Accessors(chain = true)
public class DatabaseTable {

    private final String name;
    private String schema;
    private String comment;
    private DatabasePrimaryKey primaryKey;
    private final List<DatabaseColumn> columns = new ArrayList<>();

    public DatabaseTable(String name) {
        this.name = name;
    }

    public DatabaseTable(String name, String schema) {
        this.name = name;
        this.schema = schema;
    }

    public DatabaseTable addColumn(DatabaseColumn col) {
        this.columns.add(col);
        return this;
    }
}
