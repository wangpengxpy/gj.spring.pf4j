/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security.reactive;

import gj.pf4j.core.PluginAnonymousPathRegistry;
import gj.pf4j.GJPluginAuthRegistry;
import gj.pf4j.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * WebFlux equivalent of {@code PluginDelegatingAuthFilter}.
 * <p>
 * Delegates all provider-chain execution to {@link AuthChainExecutor},
 * keeping only platform-specific concerns (body caching, SecurityContext,
 * error response).
 */
public class PluginDelegatingAuthWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(PluginDelegatingAuthWebFilter.class);

    private final GJPluginAuthRegistry registry;
    private final PluginAnonymousPathRegistry anonymousPaths;
    private final PluginAuthenticatedPathRegistry authenticatedPaths;
    private final AuthenticationStrategy strategy;
    private final ApplicationEventPublisher eventPublisher;
    private final int bodyCacheLimit;

    public PluginDelegatingAuthWebFilter(GJPluginAuthRegistry registry,
                                          PluginAnonymousPathRegistry anonymousPaths,
                                          PluginAuthenticatedPathRegistry authenticatedPaths,
                                          AuthenticationStrategy strategy,
                                          ApplicationEventPublisher eventPublisher) {
        this(registry, anonymousPaths, authenticatedPaths, strategy, eventPublisher, 64 * 1024);
    }

    public PluginDelegatingAuthWebFilter(GJPluginAuthRegistry registry,
                                          PluginAnonymousPathRegistry anonymousPaths,
                                          PluginAuthenticatedPathRegistry authenticatedPaths,
                                          AuthenticationStrategy strategy,
                                          ApplicationEventPublisher eventPublisher,
                                          int bodyCacheLimit) {
        this.registry = registry;
        this.anonymousPaths = anonymousPaths;
        this.authenticatedPaths = authenticatedPaths;
        this.strategy = strategy;
        this.eventPublisher = eventPublisher;
        this.bodyCacheLimit = bodyCacheLimit;
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE + 21;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange,
                             @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        String method = exchange.getRequest().getMethod().name();

        // 1. @AllowAnonymous → skip
        if (anonymousPaths.isAnonymous(path, method)) {
            return chain.filter(exchange);
        }

        // 2. OR auth check
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth != null && auth.isAuthenticated())
                .filter(auth -> authenticatedPaths.isPluginAuthenticated(method, path))
                .flatMap(auth -> {
                    log.debug("OR auth: session already authenticated");
                    return chain.filter(exchange);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // 3. Lookup plugin
                    String pluginId = registry.lookupPluginId(method, path);
                    if (pluginId == null) {
                        return chain.filter(exchange);
                    }

                    List<Object> providerObjs = registry.getProviders(pluginId);
                    if (providerObjs.isEmpty()) {
                        return chain.filter(exchange);
                    }

                    List<IPluginAuthenticationProvider> providers = new ArrayList<>();
                    for (Object obj : providerObjs) {
                        if (obj instanceof IPluginAuthenticationProvider p) {
                            providers.add(p);
                        }
                    }

                    // 4. Cache body, execute chain, handle result
                    return DataBufferUtils.join(exchange.getRequest().getBody(),
                                    bodyCacheLimit)
                            .map(buf -> {
                                byte[] bytes = new byte[buf.readableByteCount()];
                                buf.read(bytes);
                                DataBufferUtils.release(buf);
                                return bytes;
                            })
                            .defaultIfEmpty(new byte[0])
                            .flatMap(bodyBytes -> {
                                WebFluxHttpServletRequestAdapter adapter =
                                        new WebFluxHttpServletRequestAdapter(
                                                exchange, bodyBytes);

                                AuthChainExecutor.AuthChainResult result =
                                        AuthChainExecutor.execute(
                                                providers, adapter, strategy, pluginId);

                                return handleResult(exchange, chain, result, bodyBytes);
                            })
                            .onErrorResume(DataBufferLimitException.class, e -> {
                                log.warn("[Plugin: {}] Request body exceeds {} bytes",
                                        pluginId, bodyCacheLimit);
                                exchange.getResponse().setStatusCode(
                                        org.springframework.http.HttpStatus
                                                .PAYLOAD_TOO_LARGE);
                                return exchange.getResponse().setComplete();
                            });
                }));
    }

    private Mono<Void> handleResult(ServerWebExchange exchange, WebFilterChain chain,
                                     AuthChainExecutor.AuthChainResult result,
                                     byte[] bodyBytes) {
        if (result instanceof AuthChainExecutor.AuthChainResult.Success success) {
            Authentication auth = success.authentication();
            auth.setAuthenticated(true);
            SecurityContextImpl ctx = new SecurityContextImpl(auth);
            publishSuccess(success);
            return chain.filter(decorateWithBody(exchange, bodyBytes))
                    .contextWrite(ReactiveSecurityContextHolder
                            .withSecurityContext(Mono.just(ctx)));
        } else if (result instanceof AuthChainExecutor.AuthChainResult.Challenge challenge) {
            challenge.exception().getHeaders().forEach(
                    (k, v) -> exchange.getResponse().getHeaders().add(k, v));
            exchange.getResponse().setStatusCode(
                    org.springframework.http.HttpStatus.valueOf(
                            challenge.exception().getStatusCode()));
            publishEvent(new PluginAuthenticationFailureEvent(this,
                    challenge.pluginId(), challenge.providerName(),
                    challenge.durationMs(), challenge.exception(), challenge.order()));
            return exchange.getResponse().setComplete();
        } else if (result instanceof AuthChainExecutor.AuthChainResult.Failure failure) {
            publishChainFailure(failure);
            exchange.getResponse().setStatusCode(
                    org.springframework.http.HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        } else {
            return chain.filter(decorateWithBody(exchange, bodyBytes));
        }
    }

    // ──── Body replay ─────────────────────────────────────────────

    private static ServerWebExchange decorateWithBody(ServerWebExchange exchange,
                                                       byte[] bodyBytes) {
        return exchange.mutate()
                .request(new ServerHttpRequestDecorator(exchange.getRequest()) {
                    @Override
                    @NonNull
                    public Flux<DataBuffer> getBody() {
                        if (bodyBytes.length == 0) {
                            return Flux.empty();
                        }
                        return Flux.just(exchange.getResponse()
                                .bufferFactory().wrap(bodyBytes));
                    }
                }).build();
    }

    // ──── Event publishing ─────────────────────────────────────────

    private void publishSuccess(AuthChainExecutor.AuthChainResult.Success s) {
        if (eventPublisher == null) return;
        try {
            for (var a : s.attempts()) {
                if (a.result() != null && a.result().isAuthenticated()) {
                    publishEvent(new PluginAuthenticationSuccessEvent(this,
                            s.pluginId(), a.provider().getClass().getSimpleName(),
                            a.durationMs(), a.result(), a.provider().getOrder()));
                    return;
                }
            }
        } catch (Exception ignored) { }
    }

    private void publishChainFailure(AuthChainExecutor.AuthChainResult.Failure f) {
        if (eventPublisher == null) return;
        try {
            for (int i = f.attempts().size() - 1; i >= 0; i--) {
                var a = f.attempts().get(i);
                if (a.exception() != null) {
                    publishEvent(new PluginAuthenticationFailureEvent(this,
                            f.pluginId(), a.provider().getClass().getSimpleName(),
                            a.durationMs(), a.exception(), a.provider().getOrder()));
                    return;
                }
            }
        } catch (Exception ignored) { }
    }

    private void publishEvent(Object event) {
        try { eventPublisher.publishEvent(event); } catch (Exception ignored) { }
    }
}
