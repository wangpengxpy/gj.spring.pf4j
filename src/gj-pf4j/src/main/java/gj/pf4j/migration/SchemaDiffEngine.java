/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

class SchemaDiffEngine {

    private static final Logger log = LoggerFactory.getLogger(SchemaDiffEngine.class);

    /**
     * @param entityTables plugin entity rich model
     * @param dbTables     current database table structure
     * @return list of diff operations to execute
     */
    List<DiffOperation> diff(List<DatabaseTable> entityTables, List<DatabaseTable> dbTables) {
        List<DiffOperation> operations = new ArrayList<>();
        Map<String, DatabaseTable> dbMap = dbTables.stream()
                .collect(Collectors.toMap(t -> t.getName().toLowerCase(), t -> t, (a, b) -> a));

        for (DatabaseTable entity : entityTables) {
            DatabaseTable dbTable = dbMap.get(entity.getName().toLowerCase());
            if (dbTable == null) {
                log.debug("New table: {}", entity.getName());
                operations.add(new DiffOperation.AddTable(entity));
            } else {
                for (DatabaseColumn column : entity.getColumns()) {
                    boolean exists = dbTable.getColumns().stream()
                            .anyMatch(c -> c.getName().equalsIgnoreCase(column.getName()));
                    if (!exists) {
                        log.debug("New column: {}.{}", entity.getName(), column.getName());
                        operations.add(new DiffOperation.AddColumn(entity.getName(), column));
                    }
                }
            }
        }
        log.debug("Diff produced {} operations for {} entities", operations.size(), entityTables.size());
        return operations;
    }
}
