/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

class MigrationTracker {

    private final Object tablesLock = new Object();
    private final Map<String, Set<String>> registeredTables = new HashMap<>();

    boolean hasNewWork(Map<String, Set<String>> tableColumns) {
        synchronized (tablesLock) {
            boolean hasWork = false;
            for (Map.Entry<String, Set<String>> entry : tableColumns.entrySet()) {
                String table = entry.getKey().toLowerCase();
                Set<String> columns = entry.getValue();
                Set<String> registered = registeredTables.get(table);
                if (registered == null) {
                    Set<String> lowerColumns = new HashSet<>();
                    for (String col : columns) {
                        lowerColumns.add(col.toLowerCase());
                    }
                    registeredTables.put(table, lowerColumns);
                    hasWork = true;
                } else {
                    for (String col : columns) {
                        if (registered.add(col.toLowerCase())) {
                            hasWork = true;
                        }
                    }
                }
            }
            return hasWork;
        }
    }
}
