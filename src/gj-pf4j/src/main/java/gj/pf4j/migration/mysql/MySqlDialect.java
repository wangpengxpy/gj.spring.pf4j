/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration.mysql;

import gj.pf4j.migration.DatabaseColumn;
import gj.pf4j.migration.DatabasePrimaryKey;
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

public enum MySqlDialect implements Dialect {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(MySqlDialect.class);
    private static final LinkedHashMap<Class<?>, String> MAPPING = new LinkedHashMap<>();

    static {
        MAPPING.put(String.class,        "VARCHAR(255)");
        MAPPING.put(Long.class,          "BIGINT");
        MAPPING.put(long.class,          "BIGINT");
        MAPPING.put(Integer.class,       "INT");
        MAPPING.put(int.class,           "INT");
        MAPPING.put(Double.class,        "DOUBLE");
        MAPPING.put(double.class,        "DOUBLE");
        MAPPING.put(Float.class,         "FLOAT");
        MAPPING.put(float.class,         "FLOAT");
        MAPPING.put(Short.class,         "SMALLINT");
        MAPPING.put(short.class,         "SMALLINT");
        MAPPING.put(Boolean.class,       "BIT");
        MAPPING.put(boolean.class,       "BIT");
        MAPPING.put(LocalDateTime.class, "DATETIME");
        MAPPING.put(LocalDate.class,     "DATE");
        MAPPING.put(LocalTime.class,     "TIME");
        MAPPING.put(BigDecimal.class,    "DECIMAL(19,2)");
        MAPPING.put(UUID.class,          "VARCHAR(36)");
        MAPPING.put(byte[].class,        "LONGBLOB");
    }

    @Override public DbType dbType() { return DbType.MySQL; }
    @Override public String addColumnKeyword() { return "COLUMN"; }
    @Override public SchemaExtractor schemaExtractor() { return MySqlSchemaExtractor.INSTANCE; }
    @Override public String quoteIdentifier(String name) { return "`" + name + "`"; }

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
        StringBuilder sb = new StringBuilder();
        sb.append(nullable ? "DEFAULT NULL" : "NOT NULL");
        sb.append(defaultClause);
        String result = sb.toString();
        log.debug("[{}]   nullable={} → {}, defaultValue={} → {}",
                col.getName(), nullable, (nullable ? "DEFAULT NULL" : "NOT NULL"),
                defaultVal, defaultClause.isEmpty() ? "(none)" : defaultClause);
        return result;
    }

    @Override
    public String renderPrimaryKey(DatabasePrimaryKey pk) {
        DatabaseColumn col = pk.getColumns().get(0);
        String def = quoteIdentifier(col.getName()) + " " + col.getStoreType() + " AUTO_INCREMENT PRIMARY KEY";
        log.debug("[{}] PK: storeType={} → {}", col.getName(), col.getStoreType(), def);
        return def;
    }

    @Override
    public DdlStatement renderCreateTable(DatabaseTable table) {
        log.debug("[{}] Creating table, schema={}, {} columns",
                table.getName(), table.getSchema(), table.getColumns().size());

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(quoteIdentifier(table.getName())).append(" (\n");

        List<String> defs = new ArrayList<>();

        if (table.getPrimaryKey() != null) {
            DatabasePrimaryKey pk = table.getPrimaryKey();
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

        sb.append(String.join(",\n", defs));
        sb.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8");

        String sql = sb.toString();
        log.debug("[{}] CREATE SQL (MySQL):\n{}", table.getName(), sql);
        return DdlStatement.of(sql);
    }
}
