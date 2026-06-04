/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration.mysql;

import gj.pf4j.migration.DefaultSchemaExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * MySQL metadata extractor: passes catalog instead of schema to JDBC API, queries collation_database variable.
 */
public class MySqlSchemaExtractor extends DefaultSchemaExtractor {

    public static final MySqlSchemaExtractor INSTANCE = new MySqlSchemaExtractor();

    private static final Logger log = LoggerFactory.getLogger(MySqlSchemaExtractor.class);

    @Override
    protected boolean supportsSchema() {
        return false;
    }

    @Override
    protected String extractDefaultCollation(Connection conn) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW VARIABLES LIKE 'collation_database'")) {
            if (rs.next()) {
                return rs.getString(2);
            }
        } catch (SQLException e) {
            log.debug("Failed to extract default collation: {}", e.getMessage());
        }
        return null;
    }
}
