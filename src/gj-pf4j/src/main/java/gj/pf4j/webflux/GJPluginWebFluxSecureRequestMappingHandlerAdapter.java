/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.webflux;

import org.springframework.lang.NonNull;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class GJPluginWebFluxSecureRequestMappingHandlerAdapter extends RequestMappingHandlerAdapter {

    @Override
    @NonNull
    public Mono<HandlerResult> handle(@NonNull ServerWebExchange exchange,
                                      @NonNull Object handler) {
        return super.handle(new GJPluginWebFluxSecureServerWebExchange(exchange), handler);
    }
}
