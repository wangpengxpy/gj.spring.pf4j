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
import java.util.stream.Collectors;

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
    /** Hot-path flat index: method → (pattern → pluginId) */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<RouteEntry>> flatIndex =
            new ConcurrentHashMap<>();
    private final AntPathMatcher pathMatcher;

    private record RouteEntry(String pattern, String pluginId) {}

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
                        m.put(method, Set.copyOf(patterns)));
                return m;
            }
            methodPatterns.forEach((method, patterns) -> {
                existing.merge(method,
                        Set.copyOf(patterns),
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

        // Update flat index (hot-path acceleration)
        methodPatterns.forEach((method, patterns) -> {
            CopyOnWriteArrayList<RouteEntry> list =
                    flatIndex.computeIfAbsent(method, k -> new CopyOnWriteArrayList<>());
            for (String pattern : patterns) {
                list.addIfAbsent(new RouteEntry(pattern, pluginId));
            }
        });
    }

    @Override
    public void unregister(String pluginId) {
        // Routes MUST be removed before providers to close a TOCTOU window:
        // if providers were removed first, a concurrent request could pass
        // lookupPluginId (route still present) but get an empty provider list
        // from getProviders() — silently bypassing authentication.
        Map<String, Set<String>> removed = routeTable.remove(pluginId);
        if (removed != null) {
            removed.forEach((method, patterns) -> {
                CopyOnWriteArrayList<RouteEntry> entries = flatIndex.get(method);
                if (entries != null) {
                    entries.removeIf(e -> e.pluginId().equals(pluginId));
                    flatIndex.computeIfPresent(method, (k, v) -> v.isEmpty() ? null : v);
                }
            });
        }
        providers.remove(pluginId);
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
        CopyOnWriteArrayList<RouteEntry> entries = flatIndex.get(method);
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        String bestMatch = null;
        int bestLength = -1;
        for (RouteEntry entry : entries) {
            if (pathMatcher.match(entry.pattern(), requestUri)) {
                if (entry.pattern().length() > bestLength) {
                    bestMatch = entry.pluginId();
                    bestLength = entry.pattern().length();
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
                .map(e -> {
                    List<IPluginAuthenticationProvider> list = e.getValue();
                    String detail = list.stream()
                            .map(p -> p.getClass().getSimpleName() + ":" + p.getOrder())
                            .collect(Collectors.joining(", "));
                    return new ProviderInfo(e.getKey(), list.size(), detail);
                })
                .sorted(Comparator.comparing(ProviderInfo::pluginId))
                .toList();
    }
}