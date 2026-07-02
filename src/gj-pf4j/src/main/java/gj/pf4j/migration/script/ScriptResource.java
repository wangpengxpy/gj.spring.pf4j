/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.migration.script;

/**
 * Represents a SQL script resource discovered on the classpath.
 *
 * @param name        file name (e.g. "01-quartz-tables.sql")
 * @param order       numeric prefix parsed from file name
 * @param description description segment (e.g. "quartz-tables")
 * @param content     raw SQL content (full file body)
 */
public record ScriptResource(String name, int order, String description, String content) {
}
