/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.webflux;

import gj.pf4j.core.AnonymousRouteDeclaration;
import gj.pf4j.core.PluginAuthenticatedRouteDeclaration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class GJPluginWebFluxDefaultRouterFunctionRegistry
        implements RouterFunction<ServerResponse>, GJPluginWebFluxRouterFunctionRegistry {

    private final Collection<RouterFunction<ServerResponse>> routerFunctions;

    /** pluginId → (HTTP method → path patterns) — anonymous routes */
    private final Map<String, Map<String, Set<String>>> routerFunctionPaths = new ConcurrentHashMap<>();

    /** pluginId → (HTTP method → path patterns) — @PluginAuthenticated routes */
    private final Map<String, Map<String, Set<String>>> routerFunctionAuthPaths =
            new ConcurrentHashMap<>();

    public GJPluginWebFluxDefaultRouterFunctionRegistry() {
        this.routerFunctions = new CopyOnWriteArrayList<>();
    }

    @Override
    @NonNull
    public Mono<HandlerFunction<ServerResponse>> route(@NonNull ServerRequest request) {
        var secureServerRequest = new GJPluginWebFluxSecureServerRequest(request);
        return Flux.fromIterable(this.routerFunctions)
                .concatMap(routerFunction -> routerFunction.route(secureServerRequest)
                        .map(rf -> (HandlerFunction<ServerResponse>)
                                serverRequest -> {
                                    try {
                                        return rf.handle(secureServerRequest);
                                    } catch (LinkageError e) {
                                        return Mono.error(new ServerErrorException("detected route handle error ", e));
                                    }
                                }
                        ))
                .next();
    }

    @Override
    public void accept(@NonNull RouterFunctions.Visitor visitor) {
        this.routerFunctions.forEach(routerFunction -> routerFunction.accept(visitor));
    }

    @Override
    public void register(String pluginId,
                         Collection<RouterFunction<ServerResponse>> routerFunctions) {
        this.routerFunctions.addAll(routerFunctions);
        // Extract path patterns from AnnotatedRouterFunction declarations
        for (RouterFunction<ServerResponse> rf : routerFunctions) {
            if (rf instanceof GJRouterFunctions.AnnotatedRouterFunction arf) {
                for (AnonymousRouteDeclaration decl : arf.getDeclarations()) {
                    routerFunctionPaths
                            .computeIfAbsent(pluginId, k -> new ConcurrentHashMap<>())
                            .computeIfAbsent(decl.httpMethod().toUpperCase(),
                                    k -> ConcurrentHashMap.newKeySet())
                            .add(decl.pathPattern());
                }
                for (PluginAuthenticatedRouteDeclaration decl :
                        arf.getPluginAuthenticatedDeclarations()) {
                    routerFunctionAuthPaths
                            .computeIfAbsent(pluginId, k -> new ConcurrentHashMap<>())
                            .computeIfAbsent(decl.httpMethod().toUpperCase(),
                                    k -> ConcurrentHashMap.newKeySet())
                            .add(decl.pathPattern());
                }
            }
        }
    }

    @Override
    public void unregister(String pluginId,
                           Collection<RouterFunction<ServerResponse>> routerFunctions) {
        this.routerFunctions.removeAll(routerFunctions);
        routerFunctionPaths.remove(pluginId);
        routerFunctionAuthPaths.remove(pluginId);
    }

    @Override
    public Map<String, Set<String>> getRouterFunctionPaths(String pluginId) {
        return routerFunctionPaths.getOrDefault(pluginId, Map.of());
    }

    @Override
    public Map<String, Set<String>> getRouterFunctionAuthenticatedPaths(String pluginId) {
        return routerFunctionAuthPaths.getOrDefault(pluginId, Map.of());
    }
}
