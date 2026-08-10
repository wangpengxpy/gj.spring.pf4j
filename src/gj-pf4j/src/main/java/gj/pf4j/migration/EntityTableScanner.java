/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class EntityTableScanner {

    private static final Logger log = LoggerFactory.getLogger(EntityTableScanner.class);

    private final EntityColumnExtractor columnExtractor;

    EntityTableScanner() {
        GenericTypeResolver typeResolver = new GenericTypeResolver();
        ColumnMetaBuilder metaBuilder = new ColumnMetaBuilder(typeResolver);
        this.columnExtractor = new EntityColumnExtractor(metaBuilder);
    }

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

        // Extract all columns from the class hierarchy (handles inheritance + generics)
        List<ColumnMeta> columns = columnExtractor.extractColumns(pluginId, clazz);
        if (columns.isEmpty()) {
            log.debug("Entity {} has no mappable columns, skipping", clazz.getName());
            return null;
        }

        // Find the primary key column (set by ColumnMetaBuilder from @TableId)
        String primaryKeyColumn = null;
        String primaryKeyType = null;
        EntityTableMeta.PrimaryKeyStrategy primaryKeyStrategy = EntityTableMeta.PrimaryKeyStrategy.NONE;

        for (ColumnMeta cm : columns) {
            if (cm.isPrimaryKey()) {
                primaryKeyColumn = cm.columnName();
                primaryKeyType = cm.type().getSimpleName();
                primaryKeyStrategy = toEntityPkStrategy(cm.primaryKeyStrategy());
                break;
            }
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
                        old.columnTypeOverride(), old.jdbcType(), true, old.insertStrategy(),
                        ColumnMeta.PrimaryKeyStrategy.AUTO
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

    private static EntityTableMeta.PrimaryKeyStrategy toEntityPkStrategy(
            ColumnMeta.PrimaryKeyStrategy strategy) {
        if (strategy == null) {
            return EntityTableMeta.PrimaryKeyStrategy.NONE;
        }
        return switch (strategy) {
            case AUTO -> EntityTableMeta.PrimaryKeyStrategy.AUTO;
            case INPUT -> EntityTableMeta.PrimaryKeyStrategy.INPUT;
            case NONE -> EntityTableMeta.PrimaryKeyStrategy.NONE;
        };
    }

}
