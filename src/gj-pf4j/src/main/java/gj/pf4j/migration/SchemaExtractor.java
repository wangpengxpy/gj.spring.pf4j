/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface SchemaExtractor {
    DatabaseModel extract(Connection conn) throws SQLException;
}
