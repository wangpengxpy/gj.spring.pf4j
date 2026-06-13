/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.mybatis.interceptor;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import gj.pf4j.migration.DbType;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * MyBatis-Plus InnerInterceptor that automatically wraps column names with the
 * correct database quote character when they conflict with reserved keywords.
 *
 * <p><strong>Trigger conditions</strong> — the interceptor only rewrites SQL when ALL of the following are met:
 * <ol>
 *   <li>The SQL text contains a table name registered in {@link GJTableKeywordRegistry}</li>
 *   <li>The current database type can be identified via {@link DbType#fromConnection}</li>
 *   <li>The SQL contains registered column names that are not already quoted</li>
 * </ol>
 *
 * <p><strong>Quote character by database</strong>
 * <ul>
 *   <li>MySQL — backtick: {@code `column`}</li>
 *   <li>DM / PostgreSQL / GaussDB / KingbaseES / SQLite / Oracle — double quote: {@code "column"}</li>
 * </ul>
 *
 * <p><strong>Registration</strong>
 * Added via {@code MybatisPlusInterceptor.addInnerInterceptor(this)}.
 */
public class GJSqlKeywordQuoteInterceptor implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(GJSqlKeywordQuoteInterceptor.class);

    private volatile DbType cachedDbType;
    private volatile boolean dbTypeProbed;

    private final GJTableKeywordRegistry registry;

    public GJSqlKeywordQuoteInterceptor(GJTableKeywordRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection,
                              Integer transactionTimeout) {
        BoundSql boundSql = sh.getBoundSql();
        String rawSql = boundSql.getSql().trim();

        if (rawSql.isEmpty()) {
            return;
        }

        // 1. Quick skip: does SQL contain any registered table name?
        if (!containsAnyRegisteredTable(rawSql)) {
            return;
        }

        // 2. Detect database type, determine quote character
        String quoteChar = resolveQuoteChar(connection);
        if (quoteChar == null) {
            return;
        }

        // 3. Extract table names from SQL, look up keywords by DbType
        List<String> tableNames = extractTableNames(rawSql);
        Set<String> keywords = registry.getKeywordsForTables(tableNames, cachedDbType);
        if (keywords.isEmpty()) {
            return;
        }

        // 4. Rewrite SQL and write back
        String quotedSql = applyQuoting(rawSql, keywords, quoteChar);
        if (!quotedSql.equals(rawSql)) {
            log.debug("[KwQuote] SQL modified ({}) tables={}, keywords={}",
                    quoteChar, tableNames, keywords);
            if (log.isInfoEnabled()) {
                log.info("[KwQuote] BEFORE: {}", rawSql);
                log.info("[KwQuote] AFTER:  {}", quotedSql);
            }
            setBoundSql(sh, quotedSql);
        } else {
            log.warn("[KwQuote] Keywords registered but not matched. " +
                     "tables={}, keywords={}, dbType={}, sql={}",
                    tableNames, keywords, cachedDbType,
                    rawSql.length() > 500 ? rawSql.substring(0, 500) + "..." : rawSql);
        }
    }

    // ── Quick skip & table name extraction ───────────────────

    private boolean containsAnyRegisteredTable(String sql) {
        String lowerSql = sql.toLowerCase();
        for (String tableName : registry.getRegisteredTableNames()) {
            if (lowerSql.contains(tableName)) {
                return true;
            }
        }
        return false;
    }

    private static final Pattern TABLE_EXTRACTOR = Pattern.compile(
            "(?i)(?:FROM|JOIN|INTO|UPDATE|DELETE)\\s+" +
                    "(?:[\"'`]?([a-zA-Z_]\\w*)[\"'`]?\\.)?" +
                    "[\"'`]?([a-zA-Z_]\\w*)[\"'`]?",
            Pattern.DOTALL);

    List<String> extractTableNames(String sql) {
        Matcher matcher = TABLE_EXTRACTOR.matcher(sql);
        List<String> tableNames = new ArrayList<>();
        while (matcher.find()) {
            String tableName = matcher.group(2);
            if (tableName != null && !tableName.isEmpty()) {
                tableNames.add(tableName);
            }
        }
        return tableNames;
    }

    // ── DB type detection & quote character ──────────────────

    private String resolveQuoteChar(Connection conn) {
        if (dbTypeProbed) {
            return quoteCharFor(cachedDbType);
        }
        synchronized (this) {
            if (dbTypeProbed) {
                return quoteCharFor(cachedDbType);
            }
            try {
                cachedDbType = DbType.fromConnection(conn);
                log.info("[KwQuote] DB type detected: {}", cachedDbType);
            } catch (Exception e) {
                log.info("[KwQuote] Cannot detect DB type, interceptor disabled: {}",
                        e.getMessage());
                cachedDbType = null;
            } finally {
                dbTypeProbed = true;
            }
        }
        return quoteCharFor(cachedDbType);
    }

    private String quoteCharFor(DbType type) {
        if (type == null) {
            return null;
        }
        return type == DbType.MySQL ? "`" : "\"";
    }

    // ── SQL rewriting ────────────────────────────────────────

    /**
     * Two-pass SQL rewrite:
     * <ol>
     *   <li>Protect single-quoted string literals to avoid false matches</li>
     *   <li>Regex-replace registered keywords with quoted versions</li>
     *   <li>Restore string literals</li>
     * </ol>
     */
    private static final Pattern STRING_LITERAL = Pattern.compile(
            "'[^']*(?:''[^']*)*'");

    private String applyQuoting(String sql, Set<String> keywords, String quoteChar) {
        // Step 1: Protect string literals
        List<String> literals = new ArrayList<>();
        Matcher litMatcher = STRING_LITERAL.matcher(sql);
        String protectedSql = litMatcher.replaceAll(mr -> {
            literals.add(mr.group());
            return "___LIT_" + (literals.size() - 1) + "___";
        });

        // Step 2: Build combined regex, replace in one pass
        String result;
        if (!keywords.isEmpty()) {
            String q = Pattern.quote(quoteChar);
            String alternation = keywords.stream()
                    .map(Pattern::quote)
                    .collect(Collectors.joining("|"));
            String combinedPattern = "(?i)(?<![\\w" + q + ".])(" + alternation + ")(?![\\w" + q + "])";
            try {
                result = Pattern.compile(combinedPattern)
                        .matcher(protectedSql)
                        .replaceAll(mr -> quoteChar + mr.group() + quoteChar);
            } catch (PatternSyntaxException e) {
                log.warn("[KwQuote] Pattern compilation error: {}", e.getMessage());
                result = protectedSql;
            }
        } else {
            result = protectedSql;
        }

        // Step 3: Restore string literals
        for (int i = 0; i < literals.size(); i++) {
            result = result.replace("___LIT_" + i + "___",
                    Matcher.quoteReplacement(literals.get(i)));
        }

        return result;
    }

    // ── Write back ───────────────────────────────────────────

    /**
     * Writes the rewritten SQL back to the StatementHandler.
     * MyBatis RoutingStatementHandler delegates to an inner delegate;
     * BoundSql is stored in delegate.boundSql.sql — accessed via MetaObject.
     */
    private void setBoundSql(StatementHandler sh, String newSql) {
        MetaObject meta = SystemMetaObject.forObject(sh);
        meta.setValue("delegate.boundSql.sql", newSql);
    }
}
