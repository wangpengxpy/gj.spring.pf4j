/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.migration;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Plugin model database auto-migration orchestrator.
 * <p>
 * 7-step pipeline: scan entities → pre-check → extract DB metadata → convert to rich model → diff → generate DDL → execute DDL.
 * Thread-safe (global ReentrantLock ensures only one plugin migrates at a time).
 */
public class GJPluginModelMigrator {

    private static final Logger log = LoggerFactory.getLogger(GJPluginModelMigrator.class);
    private static final ReentrantLock migrationLock = new ReentrantLock();

    private final DataSource dataSource;
    private final EntityTableScanner entityTableScanner;
    private final JpaEntityTableMetaParser jpaEntityScanner;
    private final EntityToModelConverter modelConverter;
    private final SchemaDiffEngine diffEngine;
    private final MigrationSqlGenerator sqlGenerator;
    private final MigrationTracker tracker;
    private final GJShareModelMigrator shareModelMigrator;

    public GJPluginModelMigrator(DataSource dataSource, GJShareModelMigrator shareModelMigrator) {
        this.dataSource = dataSource;
        this.shareModelMigrator = shareModelMigrator;
        this.entityTableScanner = new EntityTableScanner();
        this.jpaEntityScanner = new JpaEntityTableMetaParser();
        this.modelConverter = new EntityToModelConverter();
        this.diffEngine = new SchemaDiffEngine();
        this.sqlGenerator = new MigrationSqlGenerator();
        this.tracker = new MigrationTracker();
    }

    /**
     * Execute database auto-migration for a single plugin.
     *
     * @param pluginId    plugin identifier
     * @param classLoader plugin classLoader for entity scanning
     */
    public void migrate(String pluginId, ClassLoader classLoader) {

        shareModelMigrator.migrateOnce(this);

        String basePackage = pluginIdToPackage(pluginId);

        migrationLock.lock();
        try {
            long t0 = System.currentTimeMillis();
            log.info("[{}] Starting database auto-migration...", pluginId);

            // Scan MyBatis-Plus entities (@TableName)
            List<EntityTableMeta> mpEntities = entityTableScanner.scan(pluginId, basePackage, classLoader);
            // Scan JPA entities (@Entity)
            List<EntityTableMeta> jpaEntities = jpaEntityScanner.scan(pluginId, basePackage, classLoader);

            // Merge, deduplicate by tableName (JPA metadata takes priority)
            List<EntityTableMeta> entities = mergeEntities(mpEntities, jpaEntities);
            if (entities.isEmpty()) {
                log.debug("[{}] No @TableName or @Entity entities found, skipping migration", pluginId);
                return;
            }
            log.info("[{}] Found {} entities to check (MyBatis: {}, JPA: {}): {}",
                    pluginId, entities.size(), mpEntities.size(), jpaEntities.size(),
                    entities.stream().map(EntityTableMeta::tableName).toList());

            doMigrate(pluginId, entities, classLoader);

            log.info("[{}] Database auto-migration completed successfully, total {}ms",
                    pluginId, System.currentTimeMillis() - t0);
        } finally {
            migrationLock.unlock();
        }
    }

    void doMigrate(String scope, List<EntityTableMeta> entities,
                   ClassLoader classLoader) {

        // Step 2: Pre-check for new work
        long t2 = System.currentTimeMillis();
        Map<String, Set<String>> tableColumns = buildTableColumns(entities);
        if (!tracker.hasNewWork(tableColumns)) {
            log.info("[{}] All tables up-to-date, migration skipped (zero DB query)", scope);
            return;
        }
        log.debug("[{}] Step2(HasNewWork): {}ms", scope, System.currentTimeMillis() - t2);

        // Step 3: Resolve dialect + extract database schema
        long t3 = System.currentTimeMillis();
        Dialect dialect = dialectFromConnection();
        DatabaseModel dbModel;
        try (Connection conn = dataSource.getConnection()) {
            dbModel = dialect.schemaExtractor().extract(conn);
        } catch (SQLException e) {
            log.error("[{}] Failed to extract database schema", scope, e);
            throw new RuntimeException("Schema extraction failed for: " + scope, e);
        }
        log.info("[{}] Database type: {}, defaultCollation: {}, {} existing tables: {}",
                scope, dialect.dbType(), dbModel.getDefaultCollation(),
                dbModel.getTables().size(),
                dbModel.getTables().stream().map(DatabaseTable::getName).toList());
        log.debug("[{}] Step3(detect dialect + extract schema): {}ms", scope, System.currentTimeMillis() - t3);

        // Step 4: Convert entity model to rich model
        long t4 = System.currentTimeMillis();
        List<DatabaseTable> entityTables = modelConverter.convert(entities, dbModel, dialect);
        log.debug("[{}] Step4(convert to model): {}ms", scope, System.currentTimeMillis() - t4);

        // Step 5: Diff comparison
        long t5 = System.currentTimeMillis();
        List<DiffOperation> operations = diffEngine.diff(entityTables, dbModel.getTables());
        if (operations.isEmpty()) {
            log.info("[{}] No schema differences, migration skipped", scope);
            return;
        }
        log.info("[{}] {} schema differences found:", scope, operations.size());
        for (DiffOperation op : operations) {
            log.info("[{}]   {}", scope, formatDiffOp(op));
        }
        log.debug("[{}] Step5(diff): {}ms", scope, System.currentTimeMillis() - t5);

        // Step 6: Generate DDL
        long t6 = System.currentTimeMillis();
        List<String> sqls = sqlGenerator.generate(operations, dialect);
        var ddlByTable = operations.stream()
                .collect(Collectors.groupingBy(DiffOperation::tableName,
                        LinkedHashMap::new, Collectors.counting()));
        log.info("[{}] Generated {} DDL statements by table: {}", scope, sqls.size(), ddlByTable);
        sqls.forEach(sql -> log.info("[{}] SQL: {}", scope, sql));
        log.debug("[{}] Step6(generate DDL): {}ms", scope, System.currentTimeMillis() - t6);

        // Step 7: Execute DDL
        long t7 = System.currentTimeMillis();
        executeDdl(scope, sqls);
        log.debug("[{}] Step7(execute DDL): {}ms", scope, System.currentTimeMillis() - t7);
    }

    // ─── Internal Methods ─────────────────────────────────────────

    private Map<String, Set<String>> buildTableColumns(List<EntityTableMeta> entities) {
        return entities.stream()
                .collect(Collectors.toMap(
                        EntityTableMeta::tableName,
                        e -> e.columns().stream()
                                .map(ColumnMeta::columnName)
                                .collect(Collectors.toCollection(HashSet::new)),
                        (a, b) -> { a.addAll(b); return a; }
                ));
    }

    private void executeDdl(String scope, List<String> sqls) {
        try (Connection conn = dataSource.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                try (Statement stmt = conn.createStatement()) {
                    for (String sql : sqls) {
                        log.info("[{}] Executing: {}", scope, sql);
                        stmt.execute(sql);
                    }
                }
                conn.commit();
                log.info("[{}] All {} DDL statements committed successfully", scope, sqls.size());
            } catch (SQLException e) {
                try {
                    conn.rollback();
                    log.warn("[{}] DDL execution rolled back", scope);
                } catch (SQLException rollbackEx) {
                    log.warn("[{}] Rollback failed (may be MySQL)", scope, rollbackEx);
                }
                log.error("[{}] DDL execution failed", scope, e);
                throw new RuntimeException("DDL execution failed for: " + scope, e);
            } finally {
                try {
                    conn.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                    // ignore
                }
            }
        } catch (SQLException e) {
            log.error("[{}] Failed to execute DDL statements", scope, e);
            throw new RuntimeException("DDL execution failed for: " + scope, e);
        }
    }

    private Dialect dialectFromConnection() {
        try (Connection conn = dataSource.getConnection()) {
            return Dialect.of(DbType.fromConnection(conn));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to detect database dialect", e);
        }
    }

    private static String formatDiffOp(DiffOperation op) {
        if (op instanceof DiffOperation.AddTable addTable) {
            return String.format("CREATE TABLE %s (%d columns, new)",
                    addTable.tableName(), addTable.entity().getColumns().size());
        } else if (op instanceof DiffOperation.AddColumn addCol) {
            return String.format("ALTER  TABLE %s ADD COLUMN %s %s",
                    addCol.tableName(), addCol.column().getName(), addCol.column().getStoreType());
        }
        return op.tableName();
    }

    private static List<EntityTableMeta> mergeEntities(List<EntityTableMeta> mpEntities,
                                                        List<EntityTableMeta> jpaEntities) {
        java.util.LinkedHashMap<String, EntityTableMeta> merged = new java.util.LinkedHashMap<>();
        for (EntityTableMeta e : mpEntities) {
            merged.put(e.tableName().toLowerCase(), e);
        }
        for (EntityTableMeta e : jpaEntities) {
            // JPA metadata takes priority for the same table
            merged.put(e.tableName().toLowerCase(), e);
        }
        return new ArrayList<>(merged.values());
    }

    private static String pluginIdToPackage(String pluginId) {
        if (pluginId == null || pluginId.trim().isEmpty()) {
            throw new IllegalArgumentException("Plugin ID must not be null or empty");
        }
        return pluginId.replace('-', '.');
    }
}
