/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import gj.pf4j.migration.dm.DmDialect;
import gj.pf4j.migration.gaussdb.GaussDBDialect;
import gj.pf4j.migration.kingbasees.KingbaseESDialect;
import gj.pf4j.migration.mysql.MySqlDialect;
import gj.pf4j.migration.oracle.OracleDialect;
import gj.pf4j.migration.postgresql.PostgreSqlDialect;
import gj.pf4j.migration.sqlite.SqliteDialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Database dialect rendering strategy.
 * Receives model objects, outputs structured DDL.
 */
public interface Dialect {

    Logger DIALECT_LOG = LoggerFactory.getLogger(Dialect.class);

    DbType dbType();

    // ── Identifiers ──────────────────────────────────────────────

    /** Wrap identifier with database-appropriate quotes (MySQL→`, PG-family→") */
    String quoteIdentifier(String name);

    // ── Type Mapping ────────────────────────────────────────────

    /**
     * Resolve type + annotation override to database column type.
     * @param type                 field type
     * @param annotationOverride   {@code @ColumnType} annotation value, may be null
     */
    String resolveStoreType(Class<?> type, String annotationOverride);

    // ── Rendering ────────────────────────────────────────────────

    /** Render single column DDL (no column name, only type + constraint + default + collation) */
    String renderColumn(DatabaseColumn col);

    /** Render primary key DDL (e.g. PRIMARY KEY(...) or column definition with auto-increment) */
    String renderPrimaryKey(DatabasePrimaryKey pk);

    /** Render complete CREATE TABLE DDL (including pre/post statements, e.g. GaussDB sequence) */
    DdlStatement renderCreateTable(DatabaseTable table);

    /** Render sequence creation SQL, returns null by default (databases without external sequences) */
    default String renderSequence(DatabaseSequence seq) {
        return null;
    }

    // ── Syntax Differences ───────────────────────────────────────

    /** COLUMN keyword in ALTER TABLE … ADD [COLUMN]; DM returns empty string */
    String addColumnKeyword();

    // ── Schema Extraction ────────────────────────────────────────

    /** Return the metadata extractor for this dialect */
    default SchemaExtractor schemaExtractor() {
        return DefaultSchemaExtractor.INSTANCE;
    }

    // ── Default Values ───────────────────────────────────────────

    /**
     * Return the DEFAULT clause for a column definition (including DEFAULT keyword), or empty string.
     * Default behavior: for NOT NULL columns with a default value, translate to SQL literal.
     * Dialects may override for specific strategies.
     */
    default String renderDefaultClause(DatabaseColumn col) {
        if (col.isNullable()) {
            DIALECT_LOG.debug("[{}] renderDefaultClause: nullable=true → SKIPPED (no DEFAULT for nullable columns)",
                    col.getName());
            return "";
        }
        Object dv = col.getDefaultValue();
        if (dv == null) {
            DIALECT_LOG.debug("[{}] renderDefaultClause: NOT NULL + defaultValue=null → no DEFAULT",
                    col.getName());
            return "";
        }
        String literal = formatSqlLiteral(dv);
        String result = " DEFAULT " + literal;
        DIALECT_LOG.debug("[{}] renderDefaultClause: NOT NULL + defaultValue={} (type={}) → '{}'",
                col.getName(), dv, dv.getClass().getSimpleName(), result);
        return result;
    }

    /** Convert default value to SQL literal; dialects may override (e.g. PG-family BOOLEAN outputs true/false) */
    default String formatSqlLiteral(Object val) {
        if (val instanceof Boolean) {
            String result = (Boolean) val ? "1" : "0";
            DIALECT_LOG.debug("  formatSqlLiteral (default): Boolean {} → '{}'", val, result);
            return result;
        }
        if (val instanceof String || val instanceof Character) {
            String result = "'" + val + "'";
            DIALECT_LOG.debug("  formatSqlLiteral (default): String/Char {} → {}", val, result);
            return result;
        }
        DIALECT_LOG.debug("  formatSqlLiteral (default): {} (type={}) → '{}'", val, val.getClass().getSimpleName(), val.toString());
        return val.toString();
    }

    // ── Factory ──────────────────────────────────────────────────

    static Dialect of(DbType type) {
        return switch (type) {
            case MySQL      -> MySqlDialect.INSTANCE;
            case PostgreSQL -> PostgreSqlDialect.INSTANCE;
            case GaussDB    -> GaussDBDialect.INSTANCE;
            case KingbaseES -> KingbaseESDialect.INSTANCE;
            case DM         -> DmDialect.INSTANCE;
            case SQLite     -> SqliteDialect.INSTANCE;
            case Oracle     -> OracleDialect.INSTANCE;
        };
    }
}
