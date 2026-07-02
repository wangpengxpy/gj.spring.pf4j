/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.migration.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Executes SQL script content against a JDBC Connection.
 * <p>
 * Splits by {@code ;} (the standard SQL delimiter), filters blank statements,
 * and applies the configured error strategy on failure.
 */
public class ScriptExecutor {

    private static final Logger log = LoggerFactory.getLogger(ScriptExecutor.class);

    private final String delimiter;
    private final boolean continueOnError;

    public ScriptExecutor(String delimiter, boolean continueOnError) {
        this.delimiter = delimiter;
        this.continueOnError = continueOnError;
    }

    public ScriptExecutor(boolean continueOnError) {
        this(";", continueOnError);
    }

    /**
     * Execute all statements in the given script.
     *
     * @throws ScriptExecutionException if a statement fails and continueOnError is false
     */
    public void execute(Connection conn, ScriptResource script)
            throws ScriptExecutionException {
        log.info("[ScriptExecutor] Executing: {}", script.name());
        String[] statements = script.content().split(delimiter);
        int count = 0;

        for (String sql : statements) {
            String trimmed = sql.trim();
            if (trimmed.isEmpty()) continue;

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(trimmed);
                count++;
            } catch (SQLException e) {
                if (continueOnError) {
                    log.error("[ScriptExecutor] Statement {} in '{}' failed: {}",
                            count + 1, script.name(), e.getMessage(), e);
                } else {
                    throw new ScriptExecutionException(
                            "Statement " + (count + 1) + " in '"
                                    + script.name() + "' failed: " + e.getMessage(), e);
                }
            }
        }
        log.info("[ScriptExecutor] '{}': {} statement(s) executed", script.name(), count);
    }
}
