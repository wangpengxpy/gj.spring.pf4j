/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

/**
 * Registry of paths annotated with {@code @PluginAuthenticated}.
 * <p>
 * During request processing, the authentication filter consults this
 * registry to distinguish OR-authentication mode (session-first, then
 * plugin auth) from exclusive-authentication mode (plugin auth only).
 */
public interface PluginAuthenticatedPathRegistry {

    /** Register a path pattern for a plugin under a specific HTTP method. */
    void register(String pluginId, String method, String pattern);

    /** Remove all registered paths for a plugin. */
    void unregister(String pluginId);

    /** Check whether the given method + path pair was registered. */
    boolean isPluginAuthenticated(String method, String path);
}
