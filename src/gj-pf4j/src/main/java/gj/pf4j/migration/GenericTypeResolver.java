/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;

/**
 * Resolves erased generic type variables on entity fields to their actual types
 * by walking the {@code originClass} → superclass {@link ParameterizedType} chain.
 * <p>
 * This class is a <b>pure function</b>: it does no logging, has no mutable state,
 * and does not depend on SLF4J or any project-specific type.  Callers receive a
 * {@link ResolvedType} that may carry a human-readable warning string; it is the
 * caller's responsibility to log it with the appropriate {@code pluginId} prefix.
 */
class GenericTypeResolver {

    /**
     * The resolved type together with an optional warning message.
     *
     * @param type    the resolved {@link Class} (never {@code null}).
     * @param warning {@code null} when resolution succeeded;
     *                otherwise a message suitable for a WARN-level log line
     *                (caller should prepend the {@code [plugin-id]} prefix).
     */
    record ResolvedType(Class<?> type, String warning) {
    }

    /**
     * Resolve the actual type of {@code field}, replacing {@link Object Object.class}
     * when the field's generic type is a {@link TypeVariable} whose binding can be
     * discovered through the superclass chain of {@code originClass}.
     *
     * @param field       the entity field whose type should be resolved.
     * @param originClass the concrete entity class (the one bearing {@code @TableName}).
     * @return a {@link ResolvedType} whose {@code type} is never {@code null}.
     */
    ResolvedType resolve(Field field, Class<?> originClass) {
        Type genericType = field.getGenericType();
        if (!(genericType instanceof TypeVariable<?> tv)) {
            return new ResolvedType(field.getType(), null);
        }

        String typeVarName = tv.getName();
        Class<?> declaringClass = field.getDeclaringClass();

        // Walk originClass → … → declaringClass, collecting TypeVariable → Type mappings
        Map<String, Type> typeVarMap = new HashMap<>();
        Class<?> currentClass = originClass;

        while (currentClass != null && currentClass != declaringClass.getSuperclass()) {
            Type genericSuper = currentClass.getGenericSuperclass();
            if (genericSuper instanceof ParameterizedType pt) {
                Class<?> rawSuper = (Class<?>) pt.getRawType();
                TypeVariable<?>[] superTypeParams = rawSuper.getTypeParameters();
                Type[] actualArgs = pt.getActualTypeArguments();
                for (int i = 0; i < superTypeParams.length; i++) {
                    String paramName = superTypeParams[i].getName();
                    Type arg = actualArgs[i];
                    if (arg instanceof Class<?> c) {
                        typeVarMap.put(paramName, c);
                    } else if (arg instanceof TypeVariable<?> argTv) {
                        Type mapped = typeVarMap.get(argTv.getName());
                        typeVarMap.put(paramName, mapped != null ? mapped : arg);
                    }
                }
                currentClass = rawSuper;
            } else {
                // Non-parameterized layer in the hierarchy — cannot resolve further
                break;
            }
        }

        Type resolved = typeVarMap.get(typeVarName);
        if (resolved instanceof Class<?> c) {
            return new ResolvedType(c, null);
        }

        return new ResolvedType(Object.class,
                "Unable to resolve type argument '" + typeVarName
                + "' for field '" + field.getName()
                + "' in " + declaringClass.getSimpleName()
                + ". Add @ColumnType to specify the column type explicitly.");
    }
}
