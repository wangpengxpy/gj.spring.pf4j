/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration.postgresql;

import gj.pf4j.migration.DefaultSchemaExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;

/**
 * PG-family metadata extractor: fallback schema to "public", uses pg_catalog.default collation.
 * Reusable by GaussDB and KingbaseES.
 */
public class PostgreSqlSchemaExtractor extends DefaultSchemaExtractor {

    public static final PostgreSqlSchemaExtractor INSTANCE = new PostgreSqlSchemaExtractor();

    private static final Logger log = LoggerFactory.getLogger(PostgreSqlSchemaExtractor.class);

    @Override
    protected String getDefaultSchema(Connection conn) {
        return "public";
    }

    @Override
    protected String extractDefaultCollation(Connection conn) {
        return "\"pg_catalog\".\"default\"";
    }
}
