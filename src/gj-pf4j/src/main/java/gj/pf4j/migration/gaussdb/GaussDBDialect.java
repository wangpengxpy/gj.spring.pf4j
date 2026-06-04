/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration.gaussdb;

import gj.pf4j.migration.DatabaseColumn;
import gj.pf4j.migration.DatabasePrimaryKey;
import gj.pf4j.migration.DatabaseSequence;
import gj.pf4j.migration.DatabaseTable;
import gj.pf4j.migration.DbType;
import gj.pf4j.migration.DdlStatement;
import gj.pf4j.migration.Dialect;
import gj.pf4j.migration.SchemaExtractor;
import gj.pf4j.migration.postgresql.PostgreSqlSchemaExtractor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.*;

public enum GaussDBDialect implements Dialect {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(GaussDBDialect.class);
    private static final LinkedHashMap<Class<?>, String> MAPPING = new LinkedHashMap<>();

    static {
        MAPPING.put(String.class,          "VARCHAR(255)");
        MAPPING.put(Long.class,            "BIGINT");
        MAPPING.put(long.class,            "BIGINT");
        MAPPING.put(Integer.class,         "INTEGER");
        MAPPING.put(int.class,             "INTEGER");
        MAPPING.put(Double.class,          "DOUBLE PRECISION");
        MAPPING.put(double.class,          "DOUBLE PRECISION");
        MAPPING.put(Float.class,           "REAL");
        MAPPING.put(float.class,           "REAL");
        MAPPING.put(Short.class,           "SMALLINT");
        MAPPING.put(short.class,           "SMALLINT");
        MAPPING.put(Boolean.class,         "BOOLEAN");
        MAPPING.put(boolean.class,         "BOOLEAN");
        MAPPING.put(LocalDateTime.class,   "TIMESTAMP");
        MAPPING.put(LocalDate.class,       "DATE");
        MAPPING.put(LocalTime.class,       "TIME");
        MAPPING.put(OffsetTime.class,      "TIMETZ");
        MAPPING.put(OffsetDateTime.class,  "TIMESTAMPTZ");
        MAPPING.put(BigDecimal.class,      "NUMERIC(19,2)");
        MAPPING.put(UUID.class,            "UUID");
        MAPPING.put(byte[].class,          "BYTEA");
    }

    @Override public DbType dbType() { return DbType.GaussDB; }
    @Override public String addColumnKeyword() { return "COLUMN"; }
    @Override public SchemaExtractor schemaExtractor() { return PostgreSqlSchemaExtractor.INSTANCE; }
    @Override public String quoteIdentifier(String name) { return "\"" + name + "\""; }

    @Override
    public String resolveStoreType(Class<?> javaType, String annotationOverride) {
        if (annotationOverride != null && !annotationOverride.isEmpty()) {
            return annotationOverride;
        }
        return MAPPING.entrySet().stream()
                .filter(e -> e.getKey().isAssignableFrom(javaType))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("VARCHAR(255)");
    }

    @Override
    public String renderColumn(DatabaseColumn col) {
        boolean nullable = col.isNullable();
        Object defaultVal = col.getDefaultValue();
        String defaultClause = renderDefaultClause(col);
        String coll = col.getCollation();
        boolean hasCollation = coll != null && !coll.isEmpty() && needsCollation(col.getStoreType());

        StringBuilder sb = new StringBuilder();
        sb.append(nullable ? "" : "NOT NULL");
        sb.append(defaultClause);
        if (hasCollation) {
            sb.append(" COLLATE ").append(coll);
        }
        log.debug("[{}]   nullable={} → {}, defaultValue={} → {}, collation={} (needsCollation={})",
                col.getName(), nullable, (nullable ? "(none)" : "NOT NULL"),
                defaultVal, defaultClause.isEmpty() ? "(none)" : defaultClause,
                coll, hasCollation);
        return sb.toString().trim();
    }

    @Override
    public String formatSqlLiteral(Object val) {
        if (val instanceof Boolean) {
            String result = (Boolean) val ? "true" : "false";
            log.debug("  formatSqlLiteral: Boolean {} → '{}'", val, result);
            return result;
        }
        return Dialect.super.formatSqlLiteral(val);
    }

    @Override
    public String renderPrimaryKey(DatabasePrimaryKey pk) {
        DatabaseColumn col = pk.getColumns().get(0);
        DatabaseSequence seq = pk.getSequence();
        if (seq != null) {
            String def = quoteIdentifier(col.getName()) + " " + col.getStoreType()
                    + " NOT NULL DEFAULT nextval('" + seq.getName() + "'::regclass) PRIMARY KEY";
            log.debug("[{}] PK: storeType={} hasSequence=true → DEFAULT nextval('{}'::regclass) PRIMARY KEY",
                    col.getName(), col.getStoreType(), seq.getName());
            return def;
        }
        String def = quoteIdentifier(col.getName()) + " " + col.getStoreType() + " NOT NULL PRIMARY KEY";
        log.debug("[{}] PK: storeType={} hasSequence=false → NOT NULL PRIMARY KEY (NO sequence!)",
                col.getName(), col.getStoreType());
        return def;
    }

    @Override
    public DdlStatement renderCreateTable(DatabaseTable table) {
        String schema = table.getSchema() != null ? table.getSchema() : "public";
        String qualifiedName = quoteIdentifier(schema) + "." + quoteIdentifier(table.getName());

        log.debug("[{}] Creating table, schema={}, {} columns",
                table.getName(), schema, table.getColumns().size());

        DdlStatement stmt = new DdlStatement();

        if (table.getPrimaryKey() != null && table.getPrimaryKey().getSequence() != null) {
            DatabaseSequence seq = table.getPrimaryKey().getSequence();
            log.debug("[{}] Sequence: name={} inc={} min={} max={} start={} cache={}",
                    table.getName(), seq.getName(), seq.getIncrement(),
                    seq.getMinValue(), seq.getMaxValue(), seq.getStart(), seq.getCache());
            stmt.addPreStatement(renderSequence(seq));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(qualifiedName).append(" (\n");

        List<String> defs = new ArrayList<>();

        if (table.getPrimaryKey() != null) {
            DatabasePrimaryKey pk = table.getPrimaryKey();
            log.debug("[{}] PK column: name={} storeType={} hasSequence={}",
                    table.getName(), pk.getColumns().get(0).getName(),
                    pk.getColumns().get(0).getStoreType(), pk.getSequence() != null);
            defs.add("  " + renderPrimaryKey(pk));
        }

        Set<String> pkColNames = new HashSet<>();
        if (table.getPrimaryKey() != null) {
            for (DatabaseColumn c : table.getPrimaryKey().getColumns()) {
                pkColNames.add(c.getName());
            }
        }
        int idx = 0;
        for (DatabaseColumn col : table.getColumns()) {
            if (pkColNames.contains(col.getName())) {
                log.debug("[{}] Col[{}]: {} → SKIPPED (PK column already rendered)",
                        table.getName(), idx++, col.getName());
                continue;
            }
            log.debug("[{}] Col[{}]: name={} storeType={} nullable={} defaultValue={} collation={}",
                    table.getName(), idx++, col.getName(), col.getStoreType(),
                    col.isNullable(), col.getDefaultValue(), col.getCollation());
            String colDef = "  " + quoteIdentifier(col.getName()) + " " + col.getStoreType() + " " + renderColumn(col);
            log.debug("[{}]   def: {}", table.getName(), colDef.trim());
            defs.add(colDef.trim());
        }

        sb.append(String.join(",\n", defs));
        sb.append("\n)");

        String sql = sb.toString();
        log.debug("[{}] CREATE SQL (GaussDB):\n{}", table.getName(), sql);
        return stmt.setMainStatement(sql);
    }

    @Override
    public String renderSequence(DatabaseSequence seq) {
        return "CREATE SEQUENCE " + quoteIdentifier(seq.getName())
                + " INCREMENT " + seq.getIncrement()
                + " MINVALUE " + seq.getMinValue()
                + " MAXVALUE " + seq.getMaxValue()
                + " START " + seq.getStart()
                + " CACHE " + seq.getCache();
    }

    private boolean needsCollation(String storeType) {
        if (storeType == null) return false;
        String upper = storeType.toUpperCase();
        return upper.contains("VARCHAR") || upper.contains("CHAR") || upper.contains("TEXT");
    }
}
