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

    private final AntPathMatcher matcher = new AntPathMatcher();

    public DefaultPluginAuthenticatedPathRegistry() {
        matcher.setCaseSensitive(false);
    }

    @Override
    public void register(String pluginId, String method, String pattern) {
        registry.computeIfAbsent(pluginId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(method.toUpperCase(), k -> new CopyOnWriteArrayList<>())
                .add(pattern);
    }

    @Override
    public void unregister(String pluginId) {
        registry.remove(pluginId);
    }

    @Override
    public boolean isPluginAuthenticated(String method, String path) {
        if (method == null || path == null) {
            return false;
        }
        String m = method.toUpperCase();
        for (ConcurrentHashMap<String, CopyOnWriteArrayList<String>> pluginMap :
                registry.values()) {
            CopyOnWriteArrayList<String> patterns = pluginMap.get(m);
            if (patterns != null) {
                for (String pattern : patterns) {
                    if (matcher.match(pattern, path)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
