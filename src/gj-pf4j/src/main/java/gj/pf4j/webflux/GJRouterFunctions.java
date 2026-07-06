/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.webflux;

import gj.pf4j.core.AnonymousRouteDeclaration;
import gj.pf4j.core.PluginAuthenticatedRouteDeclaration;
import org.springframework.lang.NonNull;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Functional DSL extension for WebFlux RouterFunction that allows
 * plugins to declare anonymous routes directly in the route definition.
 *
 * <p><strong>Common usage (90%):</strong>
 * <pre>{@code
 * import static gj.pf4j.webflux.GJRouterFunctions.route;
 *
 * @Bean
 * public RouterFunction<ServerResponse> myRoutes() {
 *     return route()
 *         .GET("/api/public/status", handler::status, "Public status check")
 *         .POST("/api/secure/data", handler::data)  // no reason = authenticated
 *         .build();
 * }
 * }</pre>
 *
 * <p><strong>Complex usage (10%):</strong>
 * <pre>{@code
 * import static gj.pf4j.webflux.GJRouterFunctions.wrap;
 *
 * RouterFunction<ServerResponse> function = RouterFunctions.route()
 *     .nest(RequestPredicates.path("/api/v2"), v2 -> v2
 *         .GET("/data", RequestPredicates.accept(JSON), handler::getData)
 *     ).build();
 *
 * return wrap(function)
 *     .anonymous("/api/v2/data", "GET", "Query data")
 *     .build();
 * }</pre>
 */
public final class GJRouterFunctions {

    private GJRouterFunctions() {
    }

    public static GJRouterFunctionBuilder route() {
        return new GJRouterFunctionBuilder();
    }

    public static GJRouterFunctionAnnotator wrap(RouterFunction<ServerResponse> function) {
        return new GJRouterFunctionAnnotator(function);
    }

    /**
     * Builder for the common case — route definition + anonymous marking in one step.
     */
    public static final class GJRouterFunctionBuilder {

        private final RouterFunctions.Builder delegate = RouterFunctions.route();

        private final List<AnonymousRouteDeclaration> declarations = new ArrayList<>();
        private final List<PluginAuthenticatedRouteDeclaration> pluginAuthDeclarations =
                new ArrayList<>();
        private String lastPathPattern;
        private String lastHttpMethod;

        private void track(String pattern, String method) {
            this.lastPathPattern = pattern;
            this.lastHttpMethod = method;
        }

        public GJRouterFunctionBuilder GET(String pattern,
                                           HandlerFunction<ServerResponse> handler) {
            delegate.GET(pattern, handler);
            track(pattern, "GET");
            declarations.add(new AnonymousRouteDeclaration(pattern, "GET", ""));
            return this;
        }

        public GJRouterFunctionBuilder GET(String pattern,
                                           HandlerFunction<ServerResponse> handler,
                                           String anonymousReason) {
            delegate.GET(pattern, handler);
            track(pattern, "GET");
            declarations.add(new AnonymousRouteDeclaration(pattern, "GET", anonymousReason));
            return this;
        }

        public GJRouterFunctionBuilder POST(String pattern,
                                            HandlerFunction<ServerResponse> handler) {
            delegate.POST(pattern, handler);
            track(pattern, "POST");
            declarations.add(new AnonymousRouteDeclaration(pattern, "POST", ""));
            return this;
        }

        public GJRouterFunctionBuilder POST(String pattern,
                                            HandlerFunction<ServerResponse> handler,
                                            String anonymousReason) {
            delegate.POST(pattern, handler);
            track(pattern, "POST");
            declarations.add(new AnonymousRouteDeclaration(pattern, "POST", anonymousReason));
            return this;
        }

        public GJRouterFunctionBuilder PUT(String pattern,
                                           HandlerFunction<ServerResponse> handler) {
            delegate.PUT(pattern, handler);
            track(pattern, "PUT");
            declarations.add(new AnonymousRouteDeclaration(pattern, "PUT", ""));
            return this;
        }

        public GJRouterFunctionBuilder PUT(String pattern,
                                           HandlerFunction<ServerResponse> handler,
                                           String anonymousReason) {
            delegate.PUT(pattern, handler);
            track(pattern, "PUT");
            declarations.add(new AnonymousRouteDeclaration(pattern, "PUT", anonymousReason));
            return this;
        }

        public GJRouterFunctionBuilder DELETE(String pattern,
                                              HandlerFunction<ServerResponse> handler) {
            delegate.DELETE(pattern, handler);
            track(pattern, "DELETE");
            declarations.add(new AnonymousRouteDeclaration(pattern, "DELETE", ""));
            return this;
        }

        public GJRouterFunctionBuilder DELETE(String pattern,
                                              HandlerFunction<ServerResponse> handler,
                                              String anonymousReason) {
            delegate.DELETE(pattern, handler);
            track(pattern, "DELETE");
            declarations.add(new AnonymousRouteDeclaration(pattern, "DELETE", anonymousReason));
            return this;
        }

        public GJRouterFunctionBuilder PATCH(String pattern,
                                             HandlerFunction<ServerResponse> handler) {
            delegate.PATCH(pattern, handler);
            track(pattern, "PATCH");
            declarations.add(new AnonymousRouteDeclaration(pattern, "PATCH", ""));
            return this;
        }

        public GJRouterFunctionBuilder PATCH(String pattern,
                                             HandlerFunction<ServerResponse> handler,
                                             String anonymousReason) {
            delegate.PATCH(pattern, handler);
            track(pattern, "PATCH");
            declarations.add(new AnonymousRouteDeclaration(pattern, "PATCH", anonymousReason));
            return this;
        }

        /**
         * Mark the immediately preceding route as plugin-authenticated.
         * Infers path and HTTP method from the last {@code GET/POST/...} call.
         */
        public GJRouterFunctionBuilder pluginAuthenticated() {
            if (lastPathPattern == null) {
                throw new IllegalStateException(
                        "No preceding route to mark as plugin-authenticated");
            }
            pluginAuthDeclarations.add(
                    new PluginAuthenticatedRouteDeclaration(lastPathPattern, lastHttpMethod));
            return this;
        }

        /**
         * Mark an arbitrary route as plugin-authenticated (OR-auth mode).
         * Use when the route was not defined directly via this builder's
         * {@code GET/POST/...} methods.
         */
        public GJRouterFunctionBuilder pluginAuthenticated(String pathPattern,
                                                            String httpMethod) {
            pluginAuthDeclarations.add(
                    new PluginAuthenticatedRouteDeclaration(pathPattern, httpMethod));
            return this;
        }

        public RouterFunction<ServerResponse> build() {
            RouterFunction<ServerResponse> function = delegate.build();
            if (declarations.isEmpty() && pluginAuthDeclarations.isEmpty()) {
                return function;
            }
            return new AnnotatedRouterFunction(function,
                    List.copyOf(declarations),
                    List.copyOf(pluginAuthDeclarations));
        }
    }

    /**
     * Annotator for the escape-hatch case — adds anonymous declarations
     * to an already-built RouterFunction.
     */
    public static final class GJRouterFunctionAnnotator {

        private final RouterFunction<ServerResponse> delegate;

        private final List<AnonymousRouteDeclaration> declarations = new ArrayList<>();
        private final List<PluginAuthenticatedRouteDeclaration> pluginAuthDeclarations =
                new ArrayList<>();

        GJRouterFunctionAnnotator(RouterFunction<ServerResponse> delegate) {
            this.delegate = delegate;
        }

        public GJRouterFunctionAnnotator anonymous(String pathPattern,
                                                    String httpMethod,
                                                    String reason) {
            declarations.add(new AnonymousRouteDeclaration(pathPattern, httpMethod, reason));
            return this;
        }

        public GJRouterFunctionAnnotator pluginAuthenticated(String pathPattern,
                                                              String httpMethod) {
            pluginAuthDeclarations.add(
                    new PluginAuthenticatedRouteDeclaration(pathPattern, httpMethod));
            return this;
        }

        public AnnotatedRouterFunction build() {
            return new AnnotatedRouterFunction(delegate,
                    List.copyOf(declarations),
                    List.copyOf(pluginAuthDeclarations));
        }
    }

    /**
     * A RouterFunction wrapper that carries anonymous route declarations.
     * Used by {@code ControllerRegistrar} to extract and register anonymous paths.
     */
    public static final class AnnotatedRouterFunction implements RouterFunction<ServerResponse> {

        private final RouterFunction<ServerResponse> delegate;

        private final List<AnonymousRouteDeclaration> declarations;
        private final List<PluginAuthenticatedRouteDeclaration> pluginAuthDeclarations;

        AnnotatedRouterFunction(RouterFunction<ServerResponse> delegate,
                                List<AnonymousRouteDeclaration> declarations,
                                List<PluginAuthenticatedRouteDeclaration> pluginAuthDeclarations) {
            this.delegate = delegate;
            this.declarations = declarations;
            this.pluginAuthDeclarations = pluginAuthDeclarations;
        }

        @Override
        @NonNull
        public Mono<HandlerFunction<ServerResponse>> route(@NonNull ServerRequest request) {
            return delegate.route(request);
        }

        @Override
        public void accept(@NonNull RouterFunctions.Visitor visitor) {
            delegate.accept(visitor);
        }

        public List<AnonymousRouteDeclaration> getDeclarations() {
            return declarations;
        }

        public List<PluginAuthenticatedRouteDeclaration> getPluginAuthenticatedDeclarations() {
            return pluginAuthDeclarations;
        }

        public RouterFunction<ServerResponse> getDelegate() {
            return delegate;
        }
    }
}
