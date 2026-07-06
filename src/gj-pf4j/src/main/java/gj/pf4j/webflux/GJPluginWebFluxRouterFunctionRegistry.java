/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.webflux;


import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface GJPluginWebFluxRouterFunctionRegistry {
    void register(String pluginId, Collection<RouterFunction<ServerResponse>> routerFunctions);
    void unregister(String pluginId, Collection<RouterFunction<ServerResponse>> routerFunctions);

    /** Get all RouterFunction path patterns for a plugin, grouped by HTTP method. */
    Map<String, Set<String>> getRouterFunctionPaths(String pluginId);

    /** Get @PluginAuthenticated RouterFunction path patterns, grouped by HTTP method. */
    Map<String, Set<String>> getRouterFunctionAuthenticatedPaths(String pluginId);
}
