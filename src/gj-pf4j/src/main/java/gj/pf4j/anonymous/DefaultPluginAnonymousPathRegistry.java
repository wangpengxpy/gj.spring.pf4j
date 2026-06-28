/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.anonymous;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link PluginAnonymousPathRegistry}.
 * <p>
 * Uses a two-level index: outer key = HTTP method ({@code GET}, {@code POST},
 * {@code *}, etc.), inner key = path pattern. On {@link #isAnonymous} calls,
 * first checks the exact HTTP method bucket, then the wildcard ({@code *})
 * bucket. Within each bucket, {@link AntPathMatcher} is used for pattern
 * matching so that {@code {variable}} and Ant-style wildcards work correctly.
 */
public class DefaultPluginAnonymousPathRegistry
        implements PluginAnonymousPathRegistry, PluginAnonymousPathRegistrar {

    private static final Logger log = LoggerFactory.getLogger(DefaultPluginAnonymousPathRegistry.class);

    /**
     * methodIndex: HTTP method → (pathPattern → AnonymousPathEntry)
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, AnonymousPathEntry>> methodIndex =
            new ConcurrentHashMap<>();

    /**
     * pluginIndex: pluginId → set of "HTTP_METHOD:pathPattern" keys
     */
    private final ConcurrentHashMap<String, Set<String>> pluginIndex = new ConcurrentHashMap<>();

    private final AntPathMatcher pathMatcher;

    public DefaultPluginAnonymousPathRegistry() {
        this.pathMatcher = new AntPathMatcher();
        this.pathMatcher.setCaseSensitive(false);
    }

    @Override
    public void register(String pluginId, AnonymousPathEntry entry) {
        String method = entry.httpMethod().toUpperCase();
        methodIndex.computeIfAbsent(method, k -> new ConcurrentHashMap<>());
        ConcurrentHashMap<String, AnonymousPathEntry> bucket = methodIndex.get(method);

        AnonymousPathEntry previous = bucket.put(entry.pathPattern(), entry);
        if (previous != null) {
            log.warn("Anonymous path conflict: {}:{} was already registered by plugin '{}', " +
                    "overwritten by plugin '{}'",
                    method, entry.pathPattern(), previous.pluginId(), pluginId);
        }

        String indexKey = toIndexKey(method, entry.pathPattern());
        pluginIndex.computeIfAbsent(pluginId, k -> ConcurrentHashMap.newKeySet()).add(indexKey);

        String reasonPart = entry.reason().isEmpty() ? "" : " (reason: " + entry.reason() + ")";
        log.info("[Plugin: {}] Registered anonymous endpoint: {} {} -> {}.{}(){}",
                pluginId, method, entry.pathPattern(),
                entry.controllerClass(), entry.methodName(), reasonPart);
    }

    @Override
    public void unregisterByPlugin(String pluginId) {
        Set<String> keys = pluginIndex.remove(pluginId);
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            String[] parts = parseIndexKey(key);
            if (parts == null) continue;
            String method = parts[0];
            String pattern = parts[1];
            ConcurrentHashMap<String, AnonymousPathEntry> bucket = methodIndex.get(method);
            if (bucket != null) {
                bucket.remove(pattern);
                if (bucket.isEmpty()) {
                    methodIndex.remove(method, new ConcurrentHashMap<>());
                }
            }
        }
        log.info("[Plugin: {}] Unregistered {} anonymous endpoints", pluginId, keys.size());
    }

    @Override
    public boolean isAnonymous(String requestPath, String httpMethod) {
        if (requestPath == null || httpMethod == null) {
            return false;
        }
        String method = httpMethod.toUpperCase();

        // check exact method bucket
        ConcurrentHashMap<String, AnonymousPathEntry> bucket = methodIndex.get(method);
        if (bucket != null && matchInBucket(bucket, requestPath)) {
            return true;
        }

        // check wildcard bucket (matches any HTTP method)
        ConcurrentHashMap<String, AnonymousPathEntry> wildcard = methodIndex.get("*");
        if (wildcard != null && matchInBucket(wildcard, requestPath)) {
            return true;
        }

        return false;
    }

    private boolean matchInBucket(Map<String, AnonymousPathEntry> bucket, String requestPath) {
        for (String pattern : bucket.keySet()) {
            if (pathMatcher.match(pattern, requestPath)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Collection<AnonymousPathEntry> listAll() {
        return methodIndex.values().stream()
                .flatMap(bucket -> bucket.values().stream())
                .toList();
    }

    @Override
    public Collection<AnonymousPathEntry> listByPlugin(String pluginId) {
        Set<String> keys = pluginIndex.get(pluginId);
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        return keys.stream()
                .map(DefaultPluginAnonymousPathRegistry::parseIndexKey)
                .filter(parts -> parts != null)
                .map(parts -> {
                    ConcurrentHashMap<String, AnonymousPathEntry> bucket = methodIndex.get(parts[0]);
                    return bucket != null ? bucket.get(parts[1]) : null;
                })
                .filter(entry -> entry != null)
                .toList();
    }

    @Override
    public int getCount() {
        return methodIndex.values().stream()
                .mapToInt(ConcurrentHashMap::size)
                .sum();
    }

    private static String toIndexKey(String httpMethod, String pathPattern) {
        return httpMethod + ":" + pathPattern;
    }

    private static String[] parseIndexKey(String key) {
        int idx = key.indexOf(':');
        if (idx < 0) {
            return null;
        }
        return new String[]{key.substring(0, idx), key.substring(idx + 1)};
    }
}
