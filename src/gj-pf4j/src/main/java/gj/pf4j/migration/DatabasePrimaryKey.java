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
public class DatabasePrimaryKey {

    private String name;
    private final List<DatabaseColumn> columns = new ArrayList<>();
    private DatabaseSequence sequence;

    public DatabasePrimaryKey addColumn(DatabaseColumn col) {
        this.columns.add(col);
        return this;
    }

    @Override
    public String toString() {
        return name != null ? name : "<UNKNOWN>";
    }
}
