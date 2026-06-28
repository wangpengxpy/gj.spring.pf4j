/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import com.baomidou.mybatisplus.annotation.FieldStrategy;

import java.util.ArrayList;
import java.util.List;

class EntityToModelConverter {

    /**
     * @param entities entity scan results
     * @param dbModel  database metadata (including default collation)
     * @param dialect  target dialect
     * @return rich model table list, ready for Dialect.renderCreateTable
     */
    List<DatabaseTable> convert(List<EntityTableMeta> entities, DatabaseModel dbModel, Dialect dialect) {
        List<DatabaseTable> tables = new ArrayList<>();
        for (EntityTableMeta entity : entities) {
            tables.add(convertTable(entity, dbModel, dialect));
        }
        return tables;
    }

    private DatabaseTable convertTable(EntityTableMeta entity, DatabaseModel dbModel, Dialect dialect) {
        DatabaseTable table = new DatabaseTable(entity.tableName(), dbModel.getDefaultSchema());

        List<DatabaseColumn> allColumns = new ArrayList<>();
        DatabaseColumn pkColumn = null;

        for (ColumnMeta cm : entity.columns()) {
            String storeType = dialect.resolveStoreType(cm.type(), cm.jdbcType(), cm.columnTypeOverride());
            boolean nullable = determineNullable(cm);

            DatabaseColumn col = new DatabaseColumn(cm.columnName(), storeType, nullable);
            col.setDefaultValue(defaultValueFor(cm.type()));
            col.setCollation(collationFor(storeType, dbModel));

            allColumns.add(col);
            if (cm.isPrimaryKey()) {
                pkColumn = col;
            }
        }
        // Primary key
        if (entity.primaryKeyColumn() != null && pkColumn != null) {
            DatabasePrimaryKey pk = new DatabasePrimaryKey();
            pk.addColumn(pkColumn);
            // GaussDB: AUTO strategy with dialect supporting renderSequence → create external sequence
            if (entity.primaryKeyStrategy() == EntityTableMeta.PrimaryKeyStrategy.AUTO) {
                DatabaseSequence seq = new DatabaseSequence(
                        entity.tableName() + "_" + entity.primaryKeyColumn() + "_seq");
                pk.setSequence(seq);
            }
            table.setPrimaryKey(pk);
        }
        table.getColumns().addAll(allColumns);
        return table;
    }

    // ── Helpers ───────────────────────────────────────────────────

    /**
     * Nullable determination priority: primary key > @TableField.insertStrategy > type-based default.
     */
    private static boolean determineNullable(ColumnMeta cm) {
        if (cm.isPrimaryKey()) {
            return false;
        }
        FieldStrategy strategy = cm.insertStrategy();
        if (strategy == FieldStrategy.NOT_NULL || strategy == FieldStrategy.NOT_EMPTY) {
            return false;
        }
        if (strategy == FieldStrategy.ALWAYS || strategy == FieldStrategy.NEVER) {
            return true;
        }
        // DEFAULT or unannotated: infer from type
        return !isNonNullableByDefault(cm.type());
    }

    /**
     * Whether a type has a sensible SQL default value, defaulting to NOT NULL.
     * Includes all primitives and their wrapper types (int/Integer → 0, boolean/Boolean → false),
     * plus Byte/Short/Long/Float/Double/Character.
     * These types automatically get a DEFAULT clause and NOT NULL when nullable is not explicitly set.
     */
    static boolean isNonNullableByDefault(Class<?> type) {
        return type.isPrimitive()
                || type == Boolean.class
                || type == Byte.class
                || type == Short.class
                || type == Integer.class
                || type == Long.class
                || type == Float.class
                || type == Double.class
                || type == Character.class;
    }

    /**
     * Default values for primitive and wrapper types (int→0, boolean→false, ...).
     */
    static Object defaultValueFor(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == char.class    || type == Character.class) return '\0';
        if (type == byte.class    || type == Byte.class)    return (byte) 0;
        if (type == short.class   || type == Short.class)   return (short) 0;
        if (type == int.class     || type == Integer.class) return 0;
        if (type == long.class    || type == Long.class)    return 0L;
        if (type == float.class   || type == Float.class)   return 0.0f;
        if (type == double.class  || type == Double.class)  return 0.0d;
        return null;
    }

    /**
     * PG-family string type columns need COLLATE; returns null for other databases.
     */
    private String collationFor(String storeType, DatabaseModel dbModel) {
        if (storeType == null) return null;
        String upper = storeType.toUpperCase();
        if (upper.contains("VARCHAR") || upper.contains("CHAR") || upper.contains("TEXT")) {
            String dbCollation = dbModel.getDefaultCollation();
            return dbCollation != null ? dbCollation : "\"pg_catalog\".\"default\"";
        }
        return null;
    }
}
