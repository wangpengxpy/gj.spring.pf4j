/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.mybatis.interceptor;

import gj.pf4j.migration.DbType;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for table-level keyword definitions, isolated by {@link DbType}.
 * <p>
 * On startup, scans the host application context for {@link GJTableKeywordProvider}
 * beans and merges their definitions. Plugins contribute keywords at runtime
 * via {@link #register(Map)} after their Spring context is refreshed.
 * <p>
 * Host applications that need per-database-type precision can inject this bean
 * and call {@link #registerBuiltin(String, Set, DbType)} directly.
 */
public class GJTableKeywordRegistry implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(GJTableKeywordRegistry.class);

    /**
     * tableName → { DbType.DM → {"context", "comment"},
     *               DbType.MySQL → {"order"} }
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<DbType, Set<String>>> tableKeywords =
            new ConcurrentHashMap<>();

    @Getter
    private volatile Set<String> registeredTableNames = Collections.emptySet();

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    void init() {
        Map<String, GJTableKeywordProvider> providers =
                applicationContext.getBeansOfType(GJTableKeywordProvider.class);
        if (!providers.isEmpty()) {
            for (GJTableKeywordProvider provider : providers.values()) {
                mergeExternal(provider.getTableKeywords());
            }
            log.info("[TableKeyword] {} host-level provider(s) loaded, {} table(s) registered: {}",
                    providers.size(), registeredTableNames.size(), registeredTableNames);
        } else {
            log.info("[TableKeyword] No host-level GJTableKeywordProvider found, registry is empty");
        }
    }

    /**
     * Register built-in keywords for a specific database type.
     * Call from host application {@code @Configuration} for per-DbType precision.
     */
    public void registerBuiltin(String table, Set<String> columns, DbType dbType) {
        Set<String> lowerCols = new HashSet<>();
        for (String col : columns) {
            lowerCols.add(col.toLowerCase());
        }
        String lowerTable = table.toLowerCase();
        ConcurrentHashMap<DbType, Set<String>> dbMap =
                tableKeywords.computeIfAbsent(lowerTable, k -> new ConcurrentHashMap<>());
        dbMap.merge(dbType, lowerCols, (existing, incoming) -> {
            existing.addAll(incoming);
            return existing;
        });
        rebuildSnapshot();
    }

    /**
     * Register plugin keywords for all database types.
     * Called by framework lifecycle after plugin context refresh.
     */
    public void register(Map<String, Set<String>> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Set<String>> entry : keywords.entrySet()) {
            for (DbType dbType : DbType.values()) {
                registerBuiltin(entry.getKey(), entry.getValue(), dbType);
            }
        }
        log.info("[TableKeyword] External keywords merged, now {} table(s) registered: {}",
                registeredTableNames.size(), registeredTableNames);
    }

    private void mergeExternal(Map<String, Set<String>> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Set<String>> entry : keywords.entrySet()) {
            for (DbType dbType : DbType.values()) {
                registerBuiltin(entry.getKey(), entry.getValue(), dbType);
            }
        }
        rebuildSnapshot();
    }

    private void rebuildSnapshot() {
        Set<String> names = new HashSet<>(tableKeywords.keySet());
        this.registeredTableNames = Collections.unmodifiableSet(names);
    }

    /**
     * Query the union of keyword column names for given tables and database type.
     *
     * @param tableNames table names extracted from SQL
     * @param dbType     current database type detected from JDBC connection
     * @return union set of column names needing quoting (lowercase), empty if none
     */
    public Set<String> getKeywordsForTables(List<String> tableNames, DbType dbType) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        for (String name : tableNames) {
            ConcurrentHashMap<DbType, Set<String>> dbMap =
                    tableKeywords.get(name.toLowerCase());
            if (dbMap == null) {
                continue;
            }
            Set<String> cols = dbMap.get(dbType);
            if (cols != null) {
                result.addAll(cols);
            }
        }
        return result;
    }
}
