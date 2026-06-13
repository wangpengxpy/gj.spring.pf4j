/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.mybatis.interceptor;

import java.util.Map;
import java.util.Set;

/**
 * Extension point for declaring table-column keyword mappings that may conflict
 * with database reserved keywords.
 * <p>
 * Both host application modules and PF4J plugins implement this interface and
 * register as Spring Beans. The framework automatically discovers and registers
 * them into {@link GJTableKeywordRegistry}.
 * <p>
 * Table names and column names are case-insensitive.
 *
 * <pre>{@code
 * // Usage in any host module or PF4J plugin:
 * @Component
 * public class MyKeywords implements GJTableKeywordProvider {
 *     @Override
 *     public Map<String, Set<String>> getTableKeywords() {
 *         return Map.of(
 *             "ep_data",  Set.of("level", "comment"),
 *             "ep_log",   Set.of("type")
 *         );
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface GJTableKeywordProvider {

    /**
     * @return {@code tableName → set of column names that need quoting},
     *         empty Map if no keywords to register
     */
    Map<String, Set<String>> getTableKeywords();
}
