/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Share model auto-migrator.
 * <p>
 * Annotate the main application with {@code @EnableGJMigration(basePackages = {...})}
 * to specify share model package paths. {@code @TableName} entities in each package
 * are migrated once before any plugin (only within the current JVM lifecycle).
 */
public class GJShareModelMigrator {

    private static final Logger log = LoggerFactory.getLogger(GJShareModelMigrator.class);

    private final AtomicBoolean migrated = new AtomicBoolean(false);
    private final ClassLoader mainClassLoader;
    private final String[] basePackages;
    private final EntityTableScanner scanner;

    public GJShareModelMigrator(ApplicationContext mainAppCtx, String[] basePackages) {
        this.mainClassLoader = mainAppCtx.getClassLoader();
        this.basePackages = (basePackages != null) ? basePackages.clone() : new String[0];
        this.scanner = new EntityTableScanner();
    }

    void migrateOnce(GJPluginModelMigrator pipeline) {
        if (!migrated.compareAndSet(false, true)) {
            return;
        }
        if (basePackages.length == 0) {
            log.info("No share model packages configured, skipping share model migration");
            return;
        }

        long t0 = System.currentTimeMillis();
        log.info("Starting share model auto-migration for {} package(s)...", basePackages.length);

        int totalEntities = 0;
        for (String pkg : basePackages) {
            String scope = "share-model:" + pkg;
            List<EntityTableMeta> entities = scanner.scan(scope, pkg, mainClassLoader);
            if (entities.isEmpty()) {
                log.info("[{}] No @TableName entities found, skipping", scope);
                continue;
            }
            log.info("[{}] Found {} share entities to check: {}", scope, entities.size(),
                    entities.stream().map(EntityTableMeta::tableName).toList());
            totalEntities += entities.size();
            pipeline.doMigrate(scope, entities, mainClassLoader);
        }

        log.info("Share model auto-migration completed, {} entities across {} package(s), total {}ms",
                totalEntities, basePackages.length, System.currentTimeMillis() - t0);
    }
}
