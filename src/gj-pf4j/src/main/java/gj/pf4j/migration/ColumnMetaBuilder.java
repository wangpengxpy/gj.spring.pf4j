/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import gj.pf4j.migration.annotation.ColumnType;
import org.apache.ibatis.type.JdbcType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * Converts a single entity {@link Field} (plus its MyBatis-Plus annotations) into a
 * {@link ColumnMeta}.  Generic-type resolution is delegated to {@link GenericTypeResolver}.
 */
class ColumnMetaBuilder {

    private static final Logger log = LoggerFactory.getLogger(ColumnMetaBuilder.class);

    private final GenericTypeResolver genericTypeResolver;

    ColumnMetaBuilder(GenericTypeResolver genericTypeResolver) {
        this.genericTypeResolver = genericTypeResolver;
    }

    /**
     * Build a {@link ColumnMeta} from a single field of an entity class.
     *
     * @param field       the reflected field.
     * @param originClass the concrete entity class (the one bearing {@code @TableName}).
     * @param pluginId    plugin identifier for log prefixing.
     * @return a populated {@link ColumnMeta}.
     */
    ColumnMeta build(Field field, Class<?> originClass, String pluginId) {
        // 1. Primary-key detection
        boolean isPrimaryKey = field.isAnnotationPresent(TableId.class);
        String columnName;
        ColumnMeta.PrimaryKeyStrategy primaryKeyStrategy;

        if (isPrimaryKey) {
            TableId tableIdAnn = field.getAnnotation(TableId.class);
            columnName = StringUtils.isNotBlank(tableIdAnn.value())
                    ? tableIdAnn.value()
                    : field.getName();
            primaryKeyStrategy = mapIdType(tableIdAnn.type());
        } else {
            primaryKeyStrategy = null;
            columnName = field.getName();
        }

        // 2. Column name / jdbcType / insertStrategy from @TableField
        JdbcType jdbcType = null;
        FieldStrategy insertStrategy = FieldStrategy.DEFAULT;
        TableField tableFieldAnn = field.getAnnotation(TableField.class);
        if (tableFieldAnn != null) {
            if (!isPrimaryKey && StringUtils.isNotBlank(tableFieldAnn.value())) {
                columnName = tableFieldAnn.value();
            }
            if (tableFieldAnn.jdbcType() != JdbcType.UNDEFINED) {
                jdbcType = tableFieldAnn.jdbcType();
            }
            insertStrategy = tableFieldAnn.insertStrategy();
        }

        // 3. Column-type override from @ColumnType
        ColumnType columnTypeAnn = field.getAnnotation(ColumnType.class);
        String columnTypeOverride = columnTypeAnn != null ? columnTypeAnn.value() : null;

        // 4. Generic type resolution (delegated)
        GenericTypeResolver.ResolvedType resolved = genericTypeResolver.resolve(field, originClass);
        if (resolved.warning() != null) {
            log.warn("[{}] {}", pluginId, resolved.warning());
        }

        // 5. Assemble
        return new ColumnMeta(
                columnName,
                field.getName(),
                resolved.type(),
                columnTypeOverride,
                jdbcType,
                isPrimaryKey,
                insertStrategy,
                primaryKeyStrategy
        );
    }

    private static ColumnMeta.PrimaryKeyStrategy mapIdType(IdType idType) {
        return switch (idType) {
            case AUTO -> ColumnMeta.PrimaryKeyStrategy.AUTO;
            case INPUT, ASSIGN_ID, ASSIGN_UUID -> ColumnMeta.PrimaryKeyStrategy.INPUT;
            case NONE -> ColumnMeta.PrimaryKeyStrategy.NONE;
        };
    }
}
