/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration.sqlite;

import gj.pf4j.migration.DatabaseColumn;
import gj.pf4j.migration.DatabasePrimaryKey;
import gj.pf4j.migration.DatabaseTable;
import gj.pf4j.migration.DbType;
import gj.pf4j.migration.DdlStatement;
import gj.pf4j.migration.Dialect;
import org.apache.ibatis.type.JdbcType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

public enum SqliteDialect implements Dialect {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(SqliteDialect.class);
    private static final LinkedHashMap<Class<?>, String> MAPPING = new LinkedHashMap<>();
    private static final EnumMap<JdbcType, String> JDBC_MAPPING = new EnumMap<>(JdbcType.class);

    static {
        MAPPING.put(String.class,        "TEXT");
        MAPPING.put(Long.class,          "INTEGER");
        MAPPING.put(long.class,          "INTEGER");
        MAPPING.put(Integer.class,       "INTEGER");
        MAPPING.put(int.class,           "INTEGER");
        MAPPING.put(Double.class,        "REAL");
        MAPPING.put(double.class,        "REAL");
        MAPPING.put(Float.class,         "REAL");
        MAPPING.put(float.class,         "REAL");
        MAPPING.put(Short.class,         "INTEGER");
        MAPPING.put(short.class,         "INTEGER");
        MAPPING.put(Boolean.class,       "INTEGER");
        MAPPING.put(boolean.class,       "INTEGER");
        MAPPING.put(LocalDateTime.class, "TEXT");
        MAPPING.put(LocalDate.class,     "TEXT");
        MAPPING.put(LocalTime.class,     "TEXT");
        MAPPING.put(BigDecimal.class,    "REAL");
        MAPPING.put(UUID.class,          "TEXT");
        MAPPING.put(byte[].class,        "BLOB");

        JDBC_MAPPING.put(JdbcType.VARCHAR,       "TEXT");
        JDBC_MAPPING.put(JdbcType.CHAR,          "TEXT");
        JDBC_MAPPING.put(JdbcType.LONGVARCHAR,   "TEXT");
        JDBC_MAPPING.put(JdbcType.CLOB,          "TEXT");
        JDBC_MAPPING.put(JdbcType.NCLOB,         "TEXT");
        JDBC_MAPPING.put(JdbcType.TINYINT,       "INTEGER");
        JDBC_MAPPING.put(JdbcType.SMALLINT,      "INTEGER");
        JDBC_MAPPING.put(JdbcType.INTEGER,       "INTEGER");
        JDBC_MAPPING.put(JdbcType.BIGINT,        "INTEGER");
        JDBC_MAPPING.put(JdbcType.FLOAT,         "REAL");
        JDBC_MAPPING.put(JdbcType.DOUBLE,        "REAL");
        JDBC_MAPPING.put(JdbcType.REAL,          "REAL");
        JDBC_MAPPING.put(JdbcType.DECIMAL,       "REAL");
        JDBC_MAPPING.put(JdbcType.NUMERIC,       "REAL");
        JDBC_MAPPING.put(JdbcType.BOOLEAN,       "INTEGER");
        JDBC_MAPPING.put(JdbcType.BIT,           "INTEGER");
        JDBC_MAPPING.put(JdbcType.DATE,          "TEXT");
        JDBC_MAPPING.put(JdbcType.TIME,          "TEXT");
        JDBC_MAPPING.put(JdbcType.TIMESTAMP,     "TEXT");
        JDBC_MAPPING.put(JdbcType.TIMESTAMP_WITH_TIMEZONE, "TEXT");
        JDBC_MAPPING.put(JdbcType.BLOB,          "BLOB");
        JDBC_MAPPING.put(JdbcType.BINARY,        "BLOB");
        JDBC_MAPPING.put(JdbcType.VARBINARY,     "BLOB");
        JDBC_MAPPING.put(JdbcType.LONGVARBINARY, "BLOB");
        JDBC_MAPPING.put(JdbcType.OTHER,         "TEXT");
    }

    @Override public DbType dbType() { return DbType.SQLite; }
    @Override public String addColumnKeyword() { return "COLUMN"; }
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
                .orElse("TEXT");
    }

    @Override
    public String renderColumn(DatabaseColumn col) {
        boolean nullable = col.isNullable();
        Object defaultVal = col.getDefaultValue();
        String defaultClause = renderDefaultClause(col);

        StringBuilder sb = new StringBuilder();
        sb.append(nullable ? "" : "NOT NULL");
        sb.append(defaultClause);
        String result = sb.toString().trim();
        log.debug("[{}]   nullable={} → {}, defaultValue={} → {}",
                col.getName(), nullable, (nullable ? "(none)" : "NOT NULL"),
                defaultVal, defaultClause.isEmpty() ? "(none)" : defaultClause);
        return result;
    }

    @Override
    public String renderPrimaryKey(DatabasePrimaryKey pk) {
        DatabaseColumn col = pk.getColumns().get(0);
        String storeType = col.getStoreType();
        boolean isInteger = storeType != null && storeType.toUpperCase().contains("INTEGER");
        if (isInteger) {
            String def = quoteIdentifier(col.getName()) + " INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL";
            log.debug("[{}] PK: storeType=INTEGER → INTEGER PRIMARY KEY AUTOINCREMENT", col.getName());
            return def;
        }
        String def = quoteIdentifier(col.getName()) + " " + storeType + " PRIMARY KEY NOT NULL";
        log.debug("[{}] PK: storeType={} → {} PRIMARY KEY (no AUTOINCREMENT)", col.getName(), storeType, storeType);
        return def;
    }

    @Override
    public DdlStatement renderCreateTable(DatabaseTable table) {
        log.debug("[{}] Creating table, {} columns", table.getName(), table.getColumns().size());

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(quoteIdentifier(table.getName())).append(" (\n");

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
        log.debug("[{}] CREATE SQL (SQLite):\n{}", table.getName(), sql);
        return DdlStatement.of(sql);
    }
}
