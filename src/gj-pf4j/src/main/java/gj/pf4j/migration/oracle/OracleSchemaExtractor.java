/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration.oracle;

import gj.pf4j.migration.DefaultSchemaExtractor;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Oracle metadata extractor: schema defaults to current user, no per-column collation.
 */
public class OracleSchemaExtractor extends DefaultSchemaExtractor {

    public static final OracleSchemaExtractor INSTANCE = new OracleSchemaExtractor();

    @Override
    protected String getDefaultSchema(Connection conn) {
        try {
            return conn.getSchema();
        } catch (SQLException e) {
            return null;
        }
    }

    @Override
    protected String extractDefaultCollation(Connection conn) {
        return null;
    }
}
