/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import gj.pf4j.migration.annotation.ColumnType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks the class hierarchy of an entity (concrete class → … → Object, excluding
 * Object itself) and extracts every database-mapped field as a {@link ColumnMeta}.
 * <p>
 * Subclass fields take precedence over parent-class fields of the same name
 * (consistent with Java field-hiding semantics).  Annotation parsing is delegated
 * to {@link ColumnMetaBuilder}.
 */
class EntityColumnExtractor {

    private static final Logger log = LoggerFactory.getLogger(EntityColumnExtractor.class);
    private static final int MAX_HIERARCHY_DEPTH = 20;

    private final ColumnMetaBuilder columnMetaBuilder;

    EntityColumnExtractor(ColumnMetaBuilder columnMetaBuilder) {
        this.columnMetaBuilder = columnMetaBuilder;
    }

    /**
     * Extract all columns from {@code clazz} and its superclasses.
     *
     * @param pluginId plugin identifier for log prefixing.
     * @param clazz    the concrete entity class ({@code @TableName}-annotated).
     * @return all mapped columns (never {@code null}, may be empty).
     */
    List<ColumnMeta> extractColumns(String pluginId, Class<?> clazz) {
        Set<String> seenFieldNames = new HashSet<>();
        List<ColumnMeta> columns = new ArrayList<>();
        int depth = 0;

        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            if (++depth > MAX_HIERARCHY_DEPTH) {
                log.error("[{}] Class hierarchy depth exceeded {} for {} — stopping traversal",
                        pluginId, MAX_HIERARCHY_DEPTH, clazz.getName());
                break;
            }
            try {
                extractFromClass(c, seenFieldNames, columns, clazz, pluginId);
            } catch (NoClassDefFoundError e) {
                log.warn("[{}] Cannot access parent class {} of {} — fields from this level "
                        + "and above will be skipped",
                        pluginId, c.getSuperclass() != null ? c.getSuperclass().getName() : "?",
                        clazz.getName());
                break;
            }
        }

        return columns;
    }

    private void extractFromClass(Class<?> currentClass, Set<String> seenFieldNames,
                                   List<ColumnMeta> columns, Class<?> originClass,
                                   String pluginId) {
        for (Field field : currentClass.getDeclaredFields()) {
            // Skip static / transient
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                continue;
            }

            // Subclass already handled this field name → shadowing
            if (seenFieldNames.contains(field.getName())) {
                detectShadowingWarning(field, currentClass, originClass, pluginId);
                continue;
            }

            // @TableField(exist = false) → skip
            TableField tableFieldAnn = field.getAnnotation(TableField.class);
            if (tableFieldAnn != null && !tableFieldAnn.exist()) {
                continue;
            }

            ColumnMeta columnMeta = columnMetaBuilder.build(field, originClass, pluginId);
            columns.add(columnMeta);
            seenFieldNames.add(field.getName());
        }
    }

    /**
     * Emit warnings when a parent-class field is shadowed by a subclass field of the
     * same name, which may cause annotation loss.
     */
    private void detectShadowingWarning(Field parentField, Class<?> parentClass,
                                         Class<?> originClass, String pluginId) {
        // Only relevant when parentClass != originClass (i.e. this field is from a parent)
        if (parentClass == originClass) {
            return;
        }

        boolean parentHasTableField = parentField.isAnnotationPresent(TableField.class);
        boolean parentHasTableId = parentField.isAnnotationPresent(TableId.class);
        boolean parentHasColumnType = parentField.isAnnotationPresent(ColumnType.class);

        if (parentHasTableField || parentHasTableId || parentHasColumnType) {
            log.warn("[{}] Field '{}' in {} shadows parent field {}.{} — "
                    + "parent annotations are lost. "
                    + "Ensure the subclass field has the intended @TableField / @TableId / @ColumnType.",
                    pluginId, parentField.getName(), originClass.getSimpleName(),
                    parentClass.getSimpleName(), parentField.getName());
        }
    }
}
