/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

import org.springframework.util.AntPathMatcher;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of {@link PluginAuthenticatedPathRegistry}.
 * <p>
 * Data structure: pluginId → HTTP method → list of path patterns.
 * Read-heavy, write-at-startup — uses {@code CopyOnWriteArrayList}
 * for lock-free reads on the request hot path.
 */
public class DefaultPluginAuthenticatedPathRegistry
        implements PluginAuthenticatedPathRegistry {

    private final ConcurrentHashMap<String,
            ConcurrentHashMap<String, CopyOnWriteArrayList<String>>> registry =
            new ConcurrentHashMap<>();
    /** Hot-path flat index: method → (pattern, pluginId) */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<PathEntry>> flatIndex =
            new ConcurrentHashMap<>();

    private record PathEntry(String pattern, String pluginId) {}

    private final AntPathMatcher matcher = new AntPathMatcher();

    public DefaultPluginAuthenticatedPathRegistry() {
        matcher.setCaseSensitive(false);
    }

    @Override
    public void register(String pluginId, String method, String pattern) {
        String m = method.toUpperCase();
        registry.computeIfAbsent(pluginId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(m, k -> new CopyOnWriteArrayList<>())
                .add(pattern);
        flatIndex.computeIfAbsent(m, k -> new CopyOnWriteArrayList<>())
                .add(new PathEntry(pattern, pluginId));
    }

    @Override
    public void unregister(String pluginId) {
        ConcurrentHashMap<String, CopyOnWriteArrayList<String>> removed =
                registry.remove(pluginId);
        if (removed != null) {
            removed.forEach((method, patterns) -> {
                CopyOnWriteArrayList<PathEntry> entries = flatIndex.get(method);
                if (entries != null) {
                    entries.removeIf(e -> e.pluginId().equals(pluginId));
                    flatIndex.computeIfPresent(method, (k, v) -> v.isEmpty() ? null : v);
                }
            });
        }
    }

    @Override
    public boolean isPluginAuthenticated(String method, String path) {
        if (method == null || path == null) {
            return false;
        }
        CopyOnWriteArrayList<PathEntry> patterns = flatIndex.get(method.toUpperCase());
        if (patterns == null) {
            return false;
        }
        for (PathEntry entry : patterns) {
            if (matcher.match(entry.pattern(), path)) {
                return true;
            }
        }
        return false;
    }
}
