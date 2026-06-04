/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Template method base class: orchestrates extraction flow, subclasses override four concerns.
 */
public abstract class AbstractSchemaExtractor implements SchemaExtractor {

    private static final Logger log = LoggerFactory.getLogger(AbstractSchemaExtractor.class);

    // ── Four concerns (subclasses may override) ──────────────────

    /** Whether JDBC {@code meta.getTables()} should pass schema. PG-family needs schema, MySQL uses catalog. */
    protected boolean supportsSchema() { return true; }

    /** Fallback value when conn.getSchema() is null. PG-family returns "public". */
    protected String getDefaultSchema(Connection conn) { return null; }

    /** Query the database default collation, returns null by default. */
    protected String extractDefaultCollation(Connection conn) { return null; }

    /** Extract all columns for a single table, using JDBC standard API by default. */
    protected List<DatabaseColumn> extractColumns(Connection conn, String catalog,
                                                   String schema, String tableName) throws SQLException {
        List<DatabaseColumn> columns = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet cols = meta.getColumns(catalog, schema, tableName, "%")) {
            while (cols.next()) {
                String colName = cols.getString("COLUMN_NAME");
                int dataType = cols.getInt("DATA_TYPE");
                int colSize = cols.getInt("COLUMN_SIZE");
                boolean nullable = cols.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
                columns.add(new DatabaseColumn(colName, dataType, colSize, nullable));
            }
        }
        return columns;
    }

    // ── Template Method ──────────────────────────────────────────

    @Override
    public DatabaseModel extract(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = conn.getCatalog();
        String schema = conn.getSchema();
        if (schema == null) {
            schema = getDefaultSchema(conn);
        }

        String collation = extractDefaultCollation(conn);
        log.debug("Extracting schema: catalog={}, schema={}, collation={}", catalog, schema, collation);

        DatabaseModel model = new DatabaseModel();
        model.setDefaultSchema(schema);
        model.setDefaultCollation(collation);

        String tableSchema = supportsSchema() ? schema : null;
        try (ResultSet rs = meta.getTables(catalog, tableSchema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (tableName == null) continue;

                DatabaseTable table = new DatabaseTable(tableName, schema);
                for (DatabaseColumn col : extractColumns(conn, catalog, tableSchema, tableName)) {
                    table.addColumn(col);
                }
                model.addTable(table);
            }
        }
        log.debug("Extracted {} tables from database", model.getTables().size());
        return model;
    }
}
