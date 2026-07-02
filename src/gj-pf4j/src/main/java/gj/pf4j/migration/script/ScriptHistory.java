/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.migration.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.time.Instant;

/**
 * Manages the {@code gj_script_history} tracking table.
 * <p>
 * Cluster safety: {@link #claim(Connection, String)} INSERTs a row with the
 * script name as primary key. In a multi-node deployment, only one node
 * succeeds — the others hit a duplicate-key violation and must skip.
 */
public class ScriptHistory {

    private static final Logger log = LoggerFactory.getLogger(ScriptHistory.class);

    static final String TABLE_NAME = "gj_script_history";

    private final DataSource dataSource;

    public ScriptHistory(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Ensure the tracking table exists on the given connection.
     */
    public void ensureTable(Connection conn) throws SQLException {
        if (tableExists(conn, TABLE_NAME)) return;
        log.info("[ScriptHistory] Creating table: {}", TABLE_NAME);
        String ddl = "CREATE TABLE " + TABLE_NAME + " ("
                + "script_name VARCHAR(255) NOT NULL, "
                + "executed_at VARCHAR(30) NOT NULL, "
                + "PRIMARY KEY (script_name))";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        }
    }

    /**
     * Claim a script for execution. INSERTs a row; returns true if this node
     * is the owner, false if another node already claimed it.
     */
    public boolean claim(Connection conn, String scriptName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("INSERT INTO " + TABLE_NAME
                    + " (script_name, executed_at) VALUES ('"
                    + scriptName + "', '" + Instant.now() + "')");
            return true;
        } catch (SQLIntegrityConstraintViolationException e) {
            log.info("[ScriptHistory] Script '{}' already claimed by another node", scriptName);
            return false;
        }
    }

    /**
     * Check whether a script was already executed (by this or another node).
     */
    public boolean isExecuted(Connection conn, String scriptName) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT 1 FROM " + TABLE_NAME
                             + " WHERE script_name = '" + scriptName + "'")) {
            return rs.next();
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(
                conn.getCatalog(), conn.getSchema(), tableName, null)) {
            return rs.next();
        }
    }
}
