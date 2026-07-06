/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import java.util.*;

/**
 * Plugin authentication registry.
 * <p>
 * Singleton bean maintained by the host application. Populated by
 * {@code AuthRegistrar} at plugin startup and queried by
 * {@code PluginDelegatingAuthFilter} and {@code PluginDelegatingAuthWebFilter}
 * on every request.
 * <p>
 * The interface lives in {@code gj-pf4j} to avoid a circular Maven dependency
 * between {@code gj-pf4j} and {@code gj-pf4j-security}.
 * The default implementation ({@code DefaultPluginAuthRegistry}) lives in
 * {@code gj-pf4j-security}.
 * <p>
 * Thread-safe: writes happen at plugin startup/stop (infrequent);
 * reads happen on the request hot path (frequent).
 */
public interface GJPluginAuthRegistry {

    /** Register an auth provider for a plugin. */
    void registerProvider(String pluginId,
                          Object /* IPluginAuthenticationProvider */ provider);

    /**
     * Register URL patterns for a plugin, grouped by HTTP method.
     * Called by ControllerRegistrar.
     */
    void registerRoutes(String pluginId, Map<String, Set<String>> methodPatterns);

    /** Remove all registrations for a plugin (called at plugin stop/unload). */
    void unregister(String pluginId);

    /**
     * Find which plugin owns the given request (method + URI).
     * Lookup first by the given HTTP method, then fall back to wildcard ({@code *}).
     * @return pluginId or {@code null} if the URI does not belong to any plugin
     */
    String lookupPluginId(String httpMethod, String requestUri);

    /**
     * Get all auth providers for a plugin, sorted by {@code getOrder()} ascending.
     * Returns an empty list if no provider is registered.
     * <p>
     * The return type is {@code List<Object>} to avoid importing
     * {@code IPluginAuthenticationProvider} from gj-pf4j-security.
     * Callers downcast each element to the concrete type.
     * <p>
     * Multi-provider semantics (inspired by Shiro {@code ModularRealmAuthenticator}):
     * providers are tried in order; first {@code supports() + authenticate()}
     * success wins; if one fails, the next is tried.
     */
    List<Object> getProviders(String pluginId);

    /** List all registered providers (for operational visibility). */
    Collection<ProviderInfo> listProviders();

    /** Snapshot of a registered provider. */
    record ProviderInfo(String pluginId, int order) {}
}
