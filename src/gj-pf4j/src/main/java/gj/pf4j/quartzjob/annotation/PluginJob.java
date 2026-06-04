/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.quartzjob.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginJob {

    /** Job unique identifier (globally unique). */
    String name();

    /** Fixed interval in seconds. Mutually exclusive with {@link #cronExpression}. Default -1 means not used. */
    long intervalSeconds() default -1;

    /** Cron expression. Mutually exclusive with {@link #intervalSeconds}. */
    String cronExpression() default "";

    /** Execute only once. */
    boolean runOnce() default false;

    /** Disallow concurrent execution. Default true. */
    boolean disallowConcurrentExecution() default true;
}
