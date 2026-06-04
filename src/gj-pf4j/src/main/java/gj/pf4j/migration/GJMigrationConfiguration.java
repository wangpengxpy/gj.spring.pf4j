/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.migration;

import gj.pf4j.migration.annotation.EnableGJMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Auto-migration {@code @Configuration}, imported by {@link EnableGJMigration}.
 * <p>
 * Registers {@link GJShareModelMigrator} and {@link GJPluginModelMigrator} beans.
 * The former handles first-time migration of share models, the latter handles
 * incremental migration of individual plugins.
 */
@Configuration
public class GJMigrationConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GJMigrationConfiguration.class);

    @Bean
    public GJShareModelMigrator shareModelMigrator(ApplicationContext appContext) {
        String[] basePackages = resolveBasePackages(appContext);
        log.info("GJ auto-migration enabled, share model packages: {}",
                basePackages.length > 0 ? String.join(", ", basePackages) : "(none)");
        return new GJShareModelMigrator(appContext, basePackages);
    }

    @Bean
    public GJPluginModelMigrator pluginModelMigrator(DataSource dataSource,
                                                      GJShareModelMigrator shareModelMigrator) {
        return new GJPluginModelMigrator(dataSource, shareModelMigrator);
    }

    private static String[] resolveBasePackages(ApplicationContext ctx) {
        String[] beanNames = ctx.getBeanNamesForAnnotation(EnableGJMigration.class);
        for (String name : beanNames) {
            EnableGJMigration ann = ctx.findAnnotationOnBean(name, EnableGJMigration.class);
            if (ann != null) {
                return ann.basePackages();
            }
        }
        return new String[0];
    }
}
