/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.migration.script;

import java.util.List;

/**
 * Scans a classpath location for SQL scripts conforming to {@code NN-description.sql}.
 * Implementations may read from different sources (classpath, file system, etc.).
 */
public interface ScriptScanner {

    /**
     * Scan the given location pattern with the provided ClassLoader.
     *
     * @param locationPattern Spring resource pattern (e.g. "classpath*:scripts/mysql/*.sql")
     * @param classLoader     ClassLoader for resource resolution
     * @return scripts sorted by numeric prefix, never null (empty if none found)
     */
    List<ScriptResource> scan(String locationPattern, ClassLoader classLoader);
}
