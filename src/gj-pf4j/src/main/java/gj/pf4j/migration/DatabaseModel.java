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
public class DatabaseModel {

    private String defaultSchema;
    private String defaultCollation;
    private final List<DatabaseTable> tables = new ArrayList<>();

    public DatabaseModel addTable(DatabaseTable t) {
        this.tables.add(t);
        return this;
    }
}
