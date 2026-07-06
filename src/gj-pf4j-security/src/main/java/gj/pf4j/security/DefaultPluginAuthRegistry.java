/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

import gj.pf4j.GJPluginAuthRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of {@link GJPluginAuthRegistry}.
 * <p>
 * Data structure:
 * <pre>
 *   providers:   pluginId → List&lt;IPluginAuthenticationProvider&gt; (sorted by getOrder())
 *   routeTable:  pluginId → Set&lt;urlPattern&gt;
 * </pre>
 * <p>
 * Route lookup uses longest-prefix {@code AntPathMatcher} match to
 * resolve ambiguous patterns (e.g. {@code /api/**} vs {@code /api/v3/**}).
 */
class DefaultPluginAuthRegistry implements GJPluginAuthRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultPluginAuthRegistry.class);

    private final ConcurrentHashMap<String, List<IPluginAuthenticationProvider>> providers =
            new ConcurrentHashMap<>();
    /** pluginId → (HTTP method → Set<pattern>) */
    private final ConcurrentHashMap<String, Map<String, Set<String>>> routeTable =
            new ConcurrentHashMap<>();
    private final AntPathMatcher pathMatcher;

    public DefaultPluginAuthRegistry() {
        this.pathMatcher = new AntPathMatcher();
        this.pathMatcher.setCaseSensitive(false);
    }

    // ──── Registration — downcast from Object (interface lives in gj-pf4j) ──

    @Override
    public void registerProvider(String pluginId, Object provider) {
        Objects.requireNonNull(pluginId, "pluginId must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        if (!(provider instanceof IPluginAuthenticationProvider p)) {
            throw new IllegalArgumentException(
                    "Expected IPluginAuthenticationProvider, got: "
                    + provider.getClass().getName());
        }
        providers.compute(pluginId, (k, list) -> {
            if (list == null) {
                list = new CopyOnWriteArrayList<>();
            }
            for (IPluginAuthenticationProvider existing : list) {
                if (existing.getClass().equals(p.getClass())) {
                    throw new IllegalArgumentException(
                            "Plugin [" + pluginId + "] already has a provider of type "
                            + p.getClass().getName());
                }
            }
            list.add(p);
            list.sort(Comparator.comparingInt(IPluginAuthenticationProvider::getOrder));
            return list;
        });
        log.info("[AuthRegistry] Registered auth provider #{} for plugin '{}': {} (order={})",
                providers.get(pluginId).size(), pluginId,
                p.getClass().getSimpleName(), p.getOrder());
    }

    @Override
    public void registerRoutes(String pluginId, Map<String, Set<String>> methodPatterns) {
        Objects.requireNonNull(pluginId, "pluginId must not be null");
        Objects.requireNonNull(methodPatterns, "methodPatterns must not be null");
        if (methodPatterns.isEmpty()) {
            return;
        }
        int totalPatterns = methodPatterns.values().stream().mapToInt(Set::size).sum();
        routeTable.compute(pluginId, (k, existing) -> {
            if (existing == null) {
                Map<String, Set<String>> m = new ConcurrentHashMap<>();
                methodPatterns.forEach((method, patterns) ->
                        m.put(method, Collections.unmodifiableSet(new HashSet<>(patterns))));
                return m;
            }
            methodPatterns.forEach((method, patterns) -> {
                existing.merge(method,
                        Collections.unmodifiableSet(new HashSet<>(patterns)),
                        (oldSet, newSet) -> {
                            Set<String> combined = new HashSet<>(oldSet);
                            combined.addAll(newSet);
                            return Collections.unmodifiableSet(combined);
                        });
            });
            return existing;
        });
        log.info("[AuthRegistry] Registered {} route(s) for plugin '{}'",
                totalPatterns, pluginId);
    }

    @Override
    public void unregister(String pluginId) {
        providers.remove(pluginId);
        routeTable.remove(pluginId);
        log.info("[AuthRegistry] Unregistered plugin '{}'", pluginId);
    }

    // ──── Lookup — hot path, must be fast ───────────────────────

    @Override
    public String lookupPluginId(String httpMethod, String requestUri) {
        if (requestUri == null || requestUri.isEmpty()) {
            return null;
        }
        String method = httpMethod != null ? httpMethod.toUpperCase() : "GET";

        // First check exact method bucket, then wildcard fallback
        return lookupByMethod(method, requestUri, false)
                .orElseGet(() -> lookupByMethod("*", requestUri, true)
                .orElse(null));
    }

    private Optional<String> lookupByMethod(String method, String requestUri,
                                              boolean wildcard) {
        String bestMatch = null;
        int bestLength = -1;
        for (Map.Entry<String, Map<String, Set<String>>> entry : routeTable.entrySet()) {
            String pluginId = entry.getKey();
            Set<String> patterns = entry.getValue().get(method);
            if (patterns == null || patterns.isEmpty()) {
                continue;
            }
            for (String pattern : patterns) {
                if (pathMatcher.match(pattern, requestUri)) {
                    if (pattern.length() > bestLength) {
                        bestMatch = pluginId;
                        bestLength = pattern.length();
                    }
                }
            }
        }
        return Optional.ofNullable(bestMatch);
    }

    @Override
    public List<Object> getProviders(String pluginId) {
        List<IPluginAuthenticationProvider> list = providers.get(pluginId);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return List.copyOf(list);
    }

    // ──── Operational visibility ────────────────────────────────

    @Override
    public Collection<ProviderInfo> listProviders() {
        return providers.entrySet().stream()
                .map(e -> new ProviderInfo(
                        e.getKey(),
                        e.getValue().isEmpty() ? 0 : e.getValue().get(0).getOrder()))
                .sorted(Comparator.comparing(ProviderInfo::pluginId))
                .toList();
    }
}
