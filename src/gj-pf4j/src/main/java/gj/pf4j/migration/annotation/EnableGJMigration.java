/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.migration.annotation;

import gj.pf4j.migration.GJMigrationConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Enable GJ PF4J database auto-migration.
 * <p>
 * Place on the main {@code @SpringBootApplication} or any {@code @Configuration} class.
 * When plugins start, {@code @TableName} entities are automatically scanned and
 * CREATE TABLE / ADD COLUMN statements are executed.
 * <p>
 * {@code basePackages} specifies share model packages (multiple allowed); entities in
 * these packages are migrated before any plugin. When this annotation is absent,
 * migration is transparently disabled (zero overhead).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(GJMigrationConfiguration.class)
public @interface EnableGJMigration {

    /** Share model package paths; @TableName entities in these packages are migrated before any plugin */
    String[] basePackages() default {};
}
