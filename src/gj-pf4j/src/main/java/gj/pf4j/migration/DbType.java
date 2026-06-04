/*
 * Copyright (c) 2025 grejeff
 */

package gj.pf4j.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * Supported database type enumeration
 */
public enum DbType {

    MySQL,
    PostgreSQL,
    GaussDB,
    KingbaseES,
    DM,
    SQLite,
    Oracle;

    public static DbType fromConnection(Connection connection) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        String name = meta.getDatabaseProductName();
        if (name == null || name.isEmpty()) {
            String url = meta.getURL();
            if (url != null) {
                if (url.contains(":mysql:"))  return MySQL;
                if (url.contains(":postgresql:")) return PostgreSQL;
                if (url.contains(":gaussdb:")) return GaussDB;
                if (url.contains(":kingbase")) return KingbaseES;
                if (url.contains(":dm:"))  return DM;
                if (url.contains(":sqlite:")) return SQLite;
                if (url.contains(":oracle:")) return Oracle;
            }
            throw new IllegalStateException("Unable to determine database type from connection");
        }
        if (name.contains("MySQL"))      return MySQL;
        if (name.contains("PostgreSQL")) return PostgreSQL;
        if (name.contains("GaussDB") || name.contains("openGauss")) return GaussDB;
        if (name.contains("Kingbase"))   return KingbaseES;
        if (name.contains("DM"))         return DM;
        if (name.contains("SQLite"))     return SQLite;
        if (name.contains("Oracle"))     return Oracle;
        throw new IllegalStateException("Unsupported database: " + name);
    }
}
