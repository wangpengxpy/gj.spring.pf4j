/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration.dm;

import gj.pf4j.migration.DatabaseColumn;
import gj.pf4j.migration.DatabasePrimaryKey;
import gj.pf4j.migration.DatabaseTable;
import gj.pf4j.migration.DbType;
import org.apache.ibatis.type.JdbcType;
import gj.pf4j.migration.DdlStatement;
import gj.pf4j.migration.Dialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.*;

public enum DmDialect implements Dialect {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(DmDialect.class);
    private static final LinkedHashMap<Class<?>, String> MAPPING = new LinkedHashMap<>();
    private static final EnumMap<JdbcType, String> JDBC_MAPPING = new EnumMap<>(JdbcType.class);

    static {
        MAPPING.put(String.class,          "VARCHAR(255 char)");
        MAPPING.put(Long.class,            "BIGINT");
        MAPPING.put(long.class,            "BIGINT");
        MAPPING.put(Integer.class,         "INT");
        MAPPING.put(int.class,             "INT");
        MAPPING.put(Double.class,          "DOUBLE PRECISION");
        MAPPING.put(double.class,          "DOUBLE PRECISION");
        MAPPING.put(Float.class,           "FLOAT");
        MAPPING.put(float.class,           "FLOAT");
        MAPPING.put(Short.class,           "SMALLINT");
        MAPPING.put(short.class,           "SMALLINT");
        MAPPING.put(Boolean.class,         "BIT");
        MAPPING.put(boolean.class,         "BIT");
        MAPPING.put(LocalDateTime.class,   "TIMESTAMP");
        MAPPING.put(LocalDate.class,       "DATE");
        MAPPING.put(LocalTime.class,       "TIME");
        MAPPING.put(OffsetTime.class,      "TIME WITH TIME ZONE");
        MAPPING.put(OffsetDateTime.class,  "TIMESTAMP WITH TIME ZONE");
        MAPPING.put(BigDecimal.class,      "NUMBER(19,2)");
        MAPPING.put(UUID.class,            "VARCHAR(36)");
        MAPPING.put(byte[].class,          "BLOB");

        JDBC_MAPPING.put(JdbcType.VARCHAR,       "VARCHAR(255 char)");
        JDBC_MAPPING.put(JdbcType.CHAR,          "CHAR(1 char)");
        JDBC_MAPPING.put(JdbcType.LONGVARCHAR,   "CLOB");
        JDBC_MAPPING.put(JdbcType.CLOB,          "CLOB");
        JDBC_MAPPING.put(JdbcType.NCLOB,         "CLOB");
        JDBC_MAPPING.put(JdbcType.TINYINT,       "SMALLINT");
        JDBC_MAPPING.put(JdbcType.SMALLINT,      "SMALLINT");
        JDBC_MAPPING.put(JdbcType.INTEGER,       "INT");
        JDBC_MAPPING.put(JdbcType.BIGINT,        "BIGINT");
        JDBC_MAPPING.put(JdbcType.FLOAT,         "FLOAT");
        JDBC_MAPPING.put(JdbcType.DOUBLE,        "DOUBLE PRECISION");
        JDBC_MAPPING.put(JdbcType.REAL,          "FLOAT");
        JDBC_MAPPING.put(JdbcType.DECIMAL,       "NUMBER(19,2)");
        JDBC_MAPPING.put(JdbcType.NUMERIC,       "NUMBER(19,2)");
        JDBC_MAPPING.put(JdbcType.BOOLEAN,       "BIT");
        JDBC_MAPPING.put(JdbcType.BIT,           "BIT");
        JDBC_MAPPING.put(JdbcType.DATE,          "DATE");
        JDBC_MAPPING.put(JdbcType.TIME,          "TIME");
        JDBC_MAPPING.put(JdbcType.TIMESTAMP,     "TIMESTAMP");
        JDBC_MAPPING.put(JdbcType.TIMESTAMP_WITH_TIMEZONE, "TIMESTAMP WITH TIME ZONE");
        JDBC_MAPPING.put(JdbcType.BLOB,          "BLOB");
        JDBC_MAPPING.put(JdbcType.BINARY,        "BLOB");
        JDBC_MAPPING.put(JdbcType.VARBINARY,     "BLOB");
        JDBC_MAPPING.put(JdbcType.LONGVARBINARY, "BLOB");
    }

    @Override public DbType dbType() { return DbType.DM; }
    @Override public String addColumnKeyword() { return ""; }
    @Override public String quoteIdentifier(String name) { return "\"" + name + "\""; }

    @Override
    public String resolveStoreType(Class<?> javaType, JdbcType jdbcType, String annotationOverride) {
        if (annotationOverride != null && !annotationOverride.isEmpty()) {
            return annotationOverride;
        }
        if (jdbcType != null && jdbcType != JdbcType.UNDEFINED) {
            String sqlType = JDBC_MAPPING.get(jdbcType);
            if (sqlType != null) {
                return sqlType;
            }
        }
        return MAPPING.entrySet().stream()
                .filter(e -> e.getKey().isAssignableFrom(javaType))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("VARCHAR(255 char)");
    }

    @Override
    public String renderColumn(DatabaseColumn col) {
        boolean nullable = col.isNullable();
        Object defaultVal = col.getDefaultValue();
        String defaultClause = renderDefaultClause(col);

        StringBuilder sb = new StringBuilder();
        sb.append(nullable ? "NULL" : "NOT NULL");
        sb.append(defaultClause);
        String result = sb.toString().trim();
        log.debug("[{}]   nullable={} → {}, defaultValue={} → {}",
                col.getName(), nullable, (nullable ? "NULL" : "NOT NULL"),
                defaultVal, defaultClause.isEmpty() ? "(none)" : defaultClause);
        return result;
    }

    @Override
    public String renderPrimaryKey(DatabasePrimaryKey pk) {
        DatabaseColumn col = pk.getColumns().get(0);
        String def = quoteIdentifier(col.getName()) + " " + col.getStoreType()
                + " IDENTITY(1,1) NOT NULL PRIMARY KEY";
        log.debug("[{}] PK: storeType={} → {}", col.getName(), col.getStoreType(), "IDENTITY(1,1)");
        return def;
    }

    @Override
    public DdlStatement renderCreateTable(DatabaseTable table) {
        String schema = table.getSchema() != null ? table.getSchema() : table.getName();
        String qualifiedName = quoteIdentifier(schema) + "." + quoteIdentifier(table.getName());

        log.debug("[{}] Creating table, schema={}, {} columns",
                table.getName(), schema, table.getColumns().size());

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(qualifiedName).append(" (\n");

        List<String> defs = new ArrayList<>();

        if (table.getPrimaryKey() != null) {
            DatabasePrimaryKey pk = table.getPrimaryKey();
            log.debug("[{}] PK column: name={} storeType={}",
                    table.getName(), pk.getColumns().get(0).getName(), pk.getColumns().get(0).getStoreType());
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
        sb.append("\n)");

        String sql = sb.toString();
        log.debug("[{}] CREATE SQL (DM):\n{}", table.getName(), sql);
        return DdlStatement.of(sql);
    }
}
