/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.webflux;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.server.ServerErrorException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class GJPluginWebFluxDefaultRouterFunctionRegistry
        implements RouterFunction<ServerResponse>, GJPluginWebFluxRouterFunctionRegistry {

    private final Collection<RouterFunction<ServerResponse>> routerFunctions;

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
    public void register(Collection<RouterFunction<ServerResponse>> routerFunctions) {
        this.routerFunctions.addAll(routerFunctions);
    }

    @Override
    public void unregister(Collection<RouterFunction<ServerResponse>> routerFunctions) {
        this.routerFunctions.removeAll(routerFunctions);
    }
}