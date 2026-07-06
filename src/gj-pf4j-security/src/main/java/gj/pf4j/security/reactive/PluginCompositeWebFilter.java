/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security.reactive;

import gj.pf4j.GJPluginFilterPosition;
import gj.pf4j.GJPluginFilterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Composite WebFlux web filter — pre-registered in the host's
 * {@code SecurityWebFilterChain} at a specific position. Delegates to
 * dynamically-registered plugin web filters (sandboxed).
 */
public class PluginCompositeWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(PluginCompositeWebFilter.class);

    private final GJPluginFilterRegistry registry;
    private final GJPluginFilterPosition position;
    private final int baseOrder;

    public PluginCompositeWebFilter(GJPluginFilterRegistry registry,
                                     GJPluginFilterPosition position,
                                     int baseOrder) {
        this.registry = registry;
        this.position = position;
        this.baseOrder = baseOrder;
    }

    @Override
    public int getOrder() {
        return baseOrder;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange,
                             @NonNull WebFilterChain chain) {
        List<WebFilter> filters = registry.getWebFilters(position);
        if (filters.isEmpty()) {
            return chain.filter(exchange);
        }

        // Build a temporary chain (reverse iteration for correct order)
        WebFilterChain wrapped = chain;
        for (int i = filters.size() - 1; i >= 0; i--) {
            WebFilter f = filters.get(i);
            WebFilterChain next = wrapped;
            wrapped = ex -> f.filter(ex, next)
                    .onErrorResume(e -> {
                        log.error("[Filter:{}] Plugin web filter error — skipping: {}",
                                position, f.getClass().getSimpleName(), e);
                        return next.filter(ex);
                    });
        }
        return wrapped.filter(exchange);
    }
}
