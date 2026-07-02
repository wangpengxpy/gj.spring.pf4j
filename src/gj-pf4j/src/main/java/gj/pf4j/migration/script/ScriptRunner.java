/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.migration.script;

import gj.pf4j.migration.DbType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Orchestrates SQL script discovery, tracking, and execution.
 * <p>
 * Two entry points:
 * <ul>
 *   <li>{@link #runFromFramework()} — framework-internal (Quartz DDL)</li>
 *   <li>{@link #runFromPlugin(String, ClassLoader)} — per-plugin scripts</li>
 * </ul>
 * Composes {@link ScriptScanner}, {@link ScriptHistory}, and {@link ScriptExecutor}.
 */
public class ScriptRunner {

    private static final Logger log = LoggerFactory.getLogger(ScriptRunner.class);
    private static final String SCRIPTS_DIR = "scripts";

    private final DataSource dataSource;
    private final ScriptScanner scanner;
    private final ScriptHistory history;
    private final ScriptExecutor executor;

    public ScriptRunner(DataSource dataSource, boolean continueOnError) {
        this.dataSource = dataSource;
        this.scanner = new ClasspathScriptScanner();
        this.history = new ScriptHistory(dataSource);
        this.executor = new ScriptExecutor(continueOnError);
    }

    /** For testing / custom extensions — inject components directly. */
    ScriptRunner(DataSource dataSource,
                 ScriptScanner scanner,
                 ScriptHistory history,
                 ScriptExecutor executor) {
        this.dataSource = dataSource;
        this.scanner = scanner;
        this.history = history;
        this.executor = executor;
    }

    /** Expose DataSource for use by GJQuartzConfig. */
    public DataSource getDataSource() {
        return dataSource;
    }

    // -- Public entry points -------------------------------------------

    /** Execute framework-bundled scripts ({@code classpath:scripts/{dbType}/}). */
    public void runFromFramework() {
        DbType dbType = detectDbType();
        String location = "classpath:" + SCRIPTS_DIR + "/"
                + dbType.name().toLowerCase() + "/*.sql";
        log.info("[ScriptRunner] Framework scripts location: {}", location);
        run(location, getClass().getClassLoader());
    }

    /** Execute scripts from a plugin classpath. */
    public void runFromPlugin(String basePackage, ClassLoader pluginClassLoader) {
        DbType dbType = detectDbType();
        String dbTypeDir = SCRIPTS_DIR + "/" + dbType.name().toLowerCase();
        String location = "classpath*:" + basePackage + "." + dbTypeDir + "/*.sql";
        log.info("[ScriptRunner] Plugin scripts location: {}", location);
        run(location, pluginClassLoader);
    }

    // -- Internal pipeline ---------------------------------------------

    private void run(String locationPattern, ClassLoader classLoader) {
        List<ScriptResource> scripts = scanner.scan(locationPattern, classLoader);
        if (scripts.isEmpty()) {
            log.debug("[ScriptRunner] No scripts found, skipping");
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            history.ensureTable(conn);

            int executed = 0;
            for (ScriptResource script : scripts) {
                if (history.isExecuted(conn, script.name())) {
                    log.debug("[ScriptRunner] '{}' already executed, skipping", script.name());
                    continue;
                }
                if (!history.claim(conn, script.name())) {
                    continue; // another node claimed it
                }
                executor.execute(conn, script);
                executed++;
            }
            log.info("[ScriptRunner] {} executed, {} skipped",
                    executed, scripts.size() - executed);
        } catch (SQLException e) {
            throw new RuntimeException("Script execution pipeline failed", e);
        }
    }

    // -- DB type detection ---------------------------------------------

    private DbType detectDbType() {
        try (Connection conn = dataSource.getConnection()) {
            return DbType.fromConnection(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to detect database type", e);
        }
    }
}
