/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.eventbus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an event class with a name for pattern matching.
 * Supports Ant-style wildcards (e.g. "user.email.*").
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventName {
    String value();
}
