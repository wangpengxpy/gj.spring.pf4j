/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import gj.pf4j.migration.annotation.ColumnType;
import org.apache.ibatis.type.JdbcType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class EntityTableScanner {

    private static final Logger log = LoggerFactory.getLogger(EntityTableScanner.class);

    List<EntityTableMeta> scan(String pluginId, String basePackage, ClassLoader pluginClassLoader) {
        log.debug("[{}] Scanning entities in package: {}", pluginId, basePackage);

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));

        ResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(pluginClassLoader);
        scanner.setResourceLoader(resolver);

        Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
        List<EntityTableMeta> result = new ArrayList<>();

        for (BeanDefinition candidate : candidates) {
            try {
                Class<?> clazz = Class.forName(
                        candidate.getBeanClassName(), false, pluginClassLoader);
                EntityTableMeta meta = parseEntity(pluginId, clazz);
                if (meta != null) {
                    result.add(meta);
                }
            } catch (Exception e) {
                log.warn("Failed to parse entity class: {}", candidate.getBeanClassName(), e);
            }
        }

        log.debug("[{}] Found {} entities in package: {}", pluginId, result.size(), basePackage);
        return result;
    }

    private EntityTableMeta parseEntity(String pluginId, Class<?> clazz) {
        TableName tableNameAnn = clazz.getAnnotation(TableName.class);
        if (tableNameAnn == null) {
            return null;
        }
        String tableName = StringUtils.isNotBlank(tableNameAnn.value())
                ? tableNameAnn.value()
                : clazz.getSimpleName();

        List<ColumnMeta> columns = new ArrayList<>();
        String primaryKeyColumn = null;
        String primaryKeyType = null;
        EntityTableMeta.PrimaryKeyStrategy primaryKeyStrategy = EntityTableMeta.PrimaryKeyStrategy.NONE;

        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            TableField tableFieldAnn = field.getAnnotation(TableField.class);
            if (tableFieldAnn != null && !tableFieldAnn.exist()) {
                continue;
            }

            boolean isPrimaryKey = field.isAnnotationPresent(TableId.class);
            String columnName;

            if (isPrimaryKey) {
                TableId tableIdAnn = field.getAnnotation(TableId.class);
                columnName = StringUtils.isNotBlank(tableIdAnn.value())
                        ? tableIdAnn.value()
                        : field.getName();
                primaryKeyColumn = columnName;
                primaryKeyType = field.getType().getSimpleName();
                primaryKeyStrategy = mapIdType(tableIdAnn.type());
            } else if (tableFieldAnn != null && StringUtils.isNotBlank(tableFieldAnn.value())) {
                columnName = tableFieldAnn.value();
            } else {
                columnName = field.getName();
            }

            ColumnType columnTypeAnn = field.getAnnotation(ColumnType.class);
            String columnTypeOverride = columnTypeAnn != null ? columnTypeAnn.value() : null;

            JdbcType jdbcType = tableFieldAnn != null && tableFieldAnn.jdbcType() != JdbcType.UNDEFINED
                    ? tableFieldAnn.jdbcType()
                    : null;

            FieldStrategy insertStrategy = tableFieldAnn != null
                    ? tableFieldAnn.insertStrategy()
                    : FieldStrategy.DEFAULT;

            columns.add(new ColumnMeta(
                    columnName,
                    field.getName(),
                    field.getType(),
                    columnTypeOverride,
                    jdbcType,
                    isPrimaryKey,
                    insertStrategy
            ));
        }
        if (columns.isEmpty()) {
            log.debug("Entity {} has no mappable columns, skipping", clazz.getName());
            return null;
        }
        // If no @TableId found, fallback to finding a field named "id" (case-insensitive)
        if (primaryKeyColumn == null) {
            int idIndex = -1;
            for (int i = 0; i < columns.size(); i++) {
                ColumnMeta cm = columns.get(i);
                if (cm.columnName().equalsIgnoreCase("id")
                        || cm.fieldName().equalsIgnoreCase("id")) {
                    idIndex = i;
                    break;
                }
            }
            if (idIndex >= 0) {
                ColumnMeta old = columns.get(idIndex);
                columns.set(idIndex, new ColumnMeta(
                        old.columnName(), old.fieldName(), old.type(),
                        old.columnTypeOverride(), old.jdbcType(), true, old.insertStrategy()
                ));
                primaryKeyColumn = old.columnName();
                primaryKeyType = old.type().getSimpleName();
                primaryKeyStrategy = EntityTableMeta.PrimaryKeyStrategy.AUTO;
                log.info("[{}] Table '{}': no @TableId found, using field '{}' (column '{}')"
                        + " as auto-increment primary key",
                        pluginId, tableName, old.fieldName(), old.columnName());
            } else {
                log.error("[{}] Table '{}' (class: {}) has no primary key set. "
                        + "Add @TableId annotation or define a field named 'id' (case-insensitive).",
                        pluginId, tableName, clazz.getName());
                throw new RuntimeException(String.format(
                        "[%s] Table '%s' (class: %s) has no primary key set. "
                        + "Add @TableId annotation or define a field named 'id' (case-insensitive).",
                        pluginId, tableName, clazz.getName()));
            }
        }

        return new EntityTableMeta(
                tableName,
                clazz.getName(),
                primaryKeyColumn,
                primaryKeyType,
                primaryKeyStrategy,
                columns
        );
    }

    private EntityTableMeta.PrimaryKeyStrategy mapIdType(IdType idType) {
        return switch (idType) {
            case AUTO -> EntityTableMeta.PrimaryKeyStrategy.AUTO;
            case INPUT, ASSIGN_ID, ASSIGN_UUID -> EntityTableMeta.PrimaryKeyStrategy.INPUT;
            case NONE -> EntityTableMeta.PrimaryKeyStrategy.NONE;
        };
    }
}
