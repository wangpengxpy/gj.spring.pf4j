package gj.pf4j.migration;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
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

class JpaEntityTableMetaParser {

    private static final Logger log = LoggerFactory.getLogger(JpaEntityTableMetaParser.class);

    List<EntityTableMeta> scan(String pluginId, String basePackage, ClassLoader classLoader) {
        log.debug("[{}] Scanning JPA entities in package: {}", pluginId, basePackage);

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        ResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(classLoader);
        scanner.setResourceLoader(resolver);

        Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
        List<EntityTableMeta> result = new ArrayList<>();

        for (BeanDefinition candidate : candidates) {
            try {
                Class<?> clazz = Class.forName(
                        candidate.getBeanClassName(), false, classLoader);
                EntityTableMeta meta = parseEntity(pluginId, clazz);
                if (meta != null) {
                    result.add(meta);
                }
            } catch (Exception e) {
                log.warn("Failed to parse JPA entity class: {}", candidate.getBeanClassName(), e);
            }
        }

        log.debug("[{}] Found {} JPA entities in package: {}", pluginId, result.size(), basePackage);
        return result;
    }

    private EntityTableMeta parseEntity(String pluginId, Class<?> clazz) {
        Table tableAnn = clazz.getAnnotation(Table.class);
        String tableName;
        if (tableAnn != null && !tableAnn.name().isBlank()) {
            tableName = tableAnn.name();
        } else {
            tableName = clazz.getSimpleName();
        }

        List<ColumnMeta> columns = new ArrayList<>();
        String primaryKeyColumn = null;
        String primaryKeyType = null;
        EntityTableMeta.PrimaryKeyStrategy primaryKeyStrategy = EntityTableMeta.PrimaryKeyStrategy.NONE;

        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.isAnnotationPresent(Transient.class)) {
                continue;
            }

            boolean isPrimaryKey = field.isAnnotationPresent(Id.class);
            String columnName;

            Column columnAnn = field.getAnnotation(Column.class);
            if (columnAnn != null && !columnAnn.name().isBlank()) {
                columnName = columnAnn.name();
            } else {
                columnName = field.getName();
            }

            if (isPrimaryKey) {
                primaryKeyColumn = columnName;
                primaryKeyType = field.getType().getSimpleName();
                GeneratedValue gvAnn = field.getAnnotation(GeneratedValue.class);
                if (gvAnn != null) {
                    primaryKeyStrategy = switch (gvAnn.strategy()) {
                        case IDENTITY -> EntityTableMeta.PrimaryKeyStrategy.AUTO;
                        default -> EntityTableMeta.PrimaryKeyStrategy.INPUT;
                    };
                } else {
                    primaryKeyStrategy = EntityTableMeta.PrimaryKeyStrategy.INPUT;
                }
            }

            FieldStrategy insertStrategy;
            if (columnAnn != null && !columnAnn.nullable()) {
                insertStrategy = FieldStrategy.NOT_NULL;
            } else {
                insertStrategy = FieldStrategy.DEFAULT;
            }

            Class<?> fieldType = field.getType();
            if (field.isAnnotationPresent(Enumerated.class)) {
                Enumerated enumAnn = field.getAnnotation(Enumerated.class);
                if (enumAnn.value() == jakarta.persistence.EnumType.STRING) {
                    fieldType = String.class;
                } else {
                    fieldType = Integer.class;
                }
            }

            String columnTypeOverride = null;
            if (columnAnn != null) {
                String colDef = columnAnn.columnDefinition();
                if (colDef != null && !colDef.isBlank()) {
                    columnTypeOverride = colDef;
                } else if (columnAnn.precision() > 0
                        && (fieldType == java.math.BigDecimal.class || fieldType == java.math.BigInteger.class)) {
                    columnTypeOverride = "DECIMAL(" + columnAnn.precision()
                            + "," + columnAnn.scale() + ")";
                }
            }

            columns.add(new ColumnMeta(
                    columnName,
                    field.getName(),
                    fieldType,
                    columnTypeOverride,
                    isPrimaryKey,
                    insertStrategy
            ));
        }

        if (columns.isEmpty()) {
            log.debug("JPA entity {} has no mappable columns, skipping", clazz.getName());
            return null;
        }

        // If no @Id found, attempt to find a field named "id" as fallback
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
                        old.columnTypeOverride(), true, old.insertStrategy()
                ));
                primaryKeyColumn = old.columnName();
                primaryKeyType = old.type().getSimpleName();
                primaryKeyStrategy = EntityTableMeta.PrimaryKeyStrategy.AUTO;
                log.info("[{}] Table '{}' (JPA): no @Id found, using field '{}' (column '{}') as auto-increment primary key",
                        pluginId, tableName, old.fieldName(), old.columnName());
            } else {
                log.error("[{}] Table '{}' (class: {}) has no primary key set. Add @Id annotation.",
                        pluginId, tableName, clazz.getName());
                throw new RuntimeException(String.format(
                        "[%s] Table '%s' (class: %s) has no primary key set. Add @Id annotation.",
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
}
