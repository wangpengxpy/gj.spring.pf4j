/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

public sealed interface DiffOperation permits DiffOperation.AddTable, DiffOperation.AddColumn {

    String tableName();

    record AddTable(DatabaseTable entity, String tableName) implements DiffOperation {
        public AddTable(DatabaseTable entity) {
            this(entity, entity.getName());
        }
    }

    record AddColumn(String tableName, DatabaseColumn column) implements DiffOperation {
    }
}
