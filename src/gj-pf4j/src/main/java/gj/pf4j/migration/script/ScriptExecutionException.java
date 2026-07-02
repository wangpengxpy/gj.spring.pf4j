/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.migration.script;

/**
 * Thrown when a SQL statement execution fails (non-recoverable).
 */
public class ScriptExecutionException extends RuntimeException {
    public ScriptExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
