/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration.oracle;

import gj.pf4j.migration.DatabaseColumn;
import gj.pf4j.migration.DatabasePrimaryKey;
import gj.pf4j.migration.DatabaseSequence;
import gj.pf4j.migration.DatabaseTable;
import gj.pf4j.migration.DbType;
import gj.pf4j.migration.DdlStatement;
import gj.pf4j.migration.Dialect;
import gj.pf4j.migration.SchemaExtractor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

public enum OracleDialect implements Dialect {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(OracleDialect.class);
    private static final LinkedHashMap<Class<?>, String> MAPPING = new LinkedHashMap<>();

    static {
        MAPPING.put(String.class,          "VARCHAR2(255)");
        MAPPING.put(Long.class,            "NUMBER(19)");
        MAPPING.put(long.class,            "NUMBER(19)");
        MAPPING.put(Integer.class,         "NUMBER(10)");
        MAPPING.put(int.class,             "NUMBER(10)");
        MAPPING.put(Double.class,          "BINARY_DOUBLE");
        MAPPING.put(double.class,          "BINARY_DOUBLE");
        MAPPING.put(Float.class,           "BINARY_FLOAT");
        MAPPING.put(float.class,           "BINARY_FLOAT");
        MAPPING.put(Short.class,           "NUMBER(5)");
        MAPPING.put(short.class,           "NUMBER(5)");
        MAPPING.put(Boolean.class,         "NUMBER(1)");
        MAPPING.put(boolean.class,         "NUMBER(1)");
        MAPPING.put(LocalDateTime.class,   "TIMESTAMP");
        MAPPING.put(LocalDate.class,       "DATE");
        MAPPING.put(LocalTime.class,       "TIMESTAMP");
        MAPPING.put(BigDecimal.class,      "NUMBER(19,2)");
        MAPPING.put(UUID.class,            "VARCHAR2(36)");
        MAPPING.put(byte[].class,          "BLOB");
    }

    @Override public DbType dbType() { return DbType.Oracle; }
    @Override public String addColumnKeyword() { return "COLUMN"; }
    @Override public SchemaExtractor schemaExtractor() { return OracleSchemaExtractor.INSTANCE; }
    @Override public String quoteIdentifier(String name) { return "\"" + name.toUpperCase() + "\""; }

    @Override
    public String resolveStoreType(Class<?> javaType, String annotationOverride) {
        if (annotationOverride != null && !annotationOverride.isEmpty()) {
            return annotationOverride;
        }
        return MAPPING.entrySet().stream()
                .filter(e -> e.getKey().isAssignableFrom(javaType))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("VARCHAR2(255)");
    }

    @Override
    public String renderColumn(DatabaseColumn col) {
        boolean nullable = col.isNullable();
        String defaultClause = renderDefaultClause(col);

        StringBuilder sb = new StringBuilder();
        sb.append(nullable ? "" : "NOT NULL");
        sb.append(defaultClause);
        String result = sb.toString().trim();
        log.debug("[{}]   nullable={} → {}, defaultValue={} → {}",
                col.getName(), nullable, (nullable ? "(none)" : "NOT NULL"),
                col.getDefaultValue(), defaultClause.isEmpty() ? "(none)" : defaultClause);
        return result;
    }

    @Override
    public String renderPrimaryKey(DatabasePrimaryKey pk) {
        DatabaseColumn col = pk.getColumns().get(0);
        DatabaseSequence seq = pk.getSequence();
        if (seq != null) {
            String def = quoteIdentifier(col.getName()) + " " + col.getStoreType()
                    + " DEFAULT " + quoteIdentifier(seq.getName()) + ".NEXTVAL NOT NULL PRIMARY KEY";
            log.debug("[{}] PK: storeType={} hasSequence=true → DEFAULT {}.NEXTVAL PRIMARY KEY",
                    col.getName(), col.getStoreType(), seq.getName().toUpperCase());
            return def;
        }
        String def = quoteIdentifier(col.getName()) + " " + col.getStoreType() + " NOT NULL PRIMARY KEY";
        log.debug("[{}] PK: storeType={} hasSequence=false → NOT NULL PRIMARY KEY",
                col.getName(), col.getStoreType());
        return def;
    }

    @Override
    public DdlStatement renderCreateTable(DatabaseTable table) {
        String schema = table.getSchema() != null ? table.getSchema() : table.getName();
        String qualifiedName = quoteIdentifier(schema) + "." + quoteIdentifier(table.getName());

        log.debug("[{}] Creating table, schema={}, {} columns",
                table.getName(), schema, table.getColumns().size());

        DdlStatement stmt = new DdlStatement();

        if (table.getPrimaryKey() != null && table.getPrimaryKey().getSequence() != null) {
            DatabaseSequence seq = table.getPrimaryKey().getSequence();
            log.debug("[{}] Sequence: name={} inc={} min={} max={} start={} cache={}",
                    table.getName(), seq.getName(), seq.getIncrement(),
                    seq.getMinValue(), seq.getMaxValue(), seq.getStart(), seq.getCache());
            stmt.addPreStatement(renderSequenceIfNotExists(seq));
        }

        StringBuilder innerDdl = new StringBuilder();
        innerDdl.append("CREATE TABLE ").append(qualifiedName).append(" (\n");

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
            log.debug("[{}] Col[{}]: name={} storeType={} nullable={} defaultValue={}",
                    table.getName(), idx++, col.getName(), col.getStoreType(),
                    col.isNullable(), col.getDefaultValue());
            String colDef = "  " + quoteIdentifier(col.getName()) + " " + col.getStoreType() + " " + renderColumn(col);
            log.debug("[{}]   def: {}", table.getName(), colDef.trim());
            defs.add(colDef.trim());
        }

        innerDdl.append(String.join(",\n", defs));
        innerDdl.append("\n)");

        String ddl = innerDdl.toString();
        log.debug("[{}] CREATE SQL (Oracle):\n{}", table.getName(), ddl);
        return stmt.setMainStatement(wrapCreateTableIfNotExists(ddl, table.getName()));
    }

    @Override
    public String renderSequence(DatabaseSequence seq) {
        return "CREATE SEQUENCE " + quoteIdentifier(seq.getName())
                + " INCREMENT BY " + seq.getIncrement()
                + " MINVALUE " + seq.getMinValue()
                + " MAXVALUE " + seq.getMaxValue()
                + " START WITH " + seq.getStart()
                + " CACHE " + seq.getCache();
    }

    private String renderSequenceIfNotExists(DatabaseSequence seq) {
        String createSql = renderSequence(seq).replace("'", "''");
        return "DECLARE v_count NUMBER; BEGIN "
                + "SELECT COUNT(*) INTO v_count FROM user_sequences"
                + " WHERE sequence_name = '" + seq.getName().toUpperCase() + "'; "
                + "IF v_count = 0 THEN "
                + "EXECUTE IMMEDIATE '" + createSql + "'; "
                + "END IF; END;";
    }

    private String wrapCreateTableIfNotExists(String ddl, String tableName) {
        String escaped = ddl.replace("'", "''");
        return "BEGIN\n"
                + "  EXECUTE IMMEDIATE '" + escaped + "';\n"
                + "EXCEPTION WHEN OTHERS THEN\n"
                + "  IF SQLCODE != -955 THEN RAISE; END IF;\n"
                + "END;";
    }
}
