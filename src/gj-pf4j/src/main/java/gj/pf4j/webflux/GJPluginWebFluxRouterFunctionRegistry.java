/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.webflux;


import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.Collection;

public interface GJPluginWebFluxRouterFunctionRegistry {
    void register(Collection<RouterFunction<ServerResponse>> routerFunctions);
    void unregister(Collection<RouterFunction<ServerResponse>> routerFunctions);
}
