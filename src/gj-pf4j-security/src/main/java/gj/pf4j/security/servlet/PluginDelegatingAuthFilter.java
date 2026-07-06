/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security.servlet;

import gj.pf4j.core.PluginAnonymousPathRegistry;
import gj.pf4j.GJPluginAuthRegistry;
import gj.pf4j.security.*;
import gj.pf4j.utils.PluginHttpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Delegates authentication to plugin-registered providers.
 * <p>
 * Delegates all provider-chain execution to {@link AuthChainExecutor},
 * keeping only platform-specific concerns (SecurityContext, error response).
 */
public class PluginDelegatingAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PluginDelegatingAuthFilter.class);

    private final GJPluginAuthRegistry registry;
    private final PluginAnonymousPathRegistry anonymousPaths;
    private final PluginAuthenticatedPathRegistry authenticatedPaths;
    private final AuthenticationStrategy strategy;
    private final ApplicationEventPublisher eventPublisher;
    private final int bodyCacheLimit;

    public PluginDelegatingAuthFilter(GJPluginAuthRegistry registry,
                                       PluginAnonymousPathRegistry anonymousPaths,
                                       PluginAuthenticatedPathRegistry authenticatedPaths,
                                       AuthenticationStrategy strategy,
                                       ApplicationEventPublisher eventPublisher) {
        this(registry, anonymousPaths, authenticatedPaths, strategy, eventPublisher, 64 * 1024);
    }

    public PluginDelegatingAuthFilter(GJPluginAuthRegistry registry,
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
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        ContentCachingRequestWrapper wrappedRequest =
                new ContentCachingRequestWrapper(request, bodyCacheLimit);
        String path = PluginHttpUtils.getPathWithinApplication(wrappedRequest);

        // 1. @AllowAnonymous → skip
        if (anonymousPaths.isAnonymous(path, wrappedRequest.getMethod())) {
            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        // 2. OR auth: session present + @PluginAuthenticated → session first
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuth != null && existingAuth.isAuthenticated()) {
            if (authenticatedPaths.isPluginAuthenticated(
                    wrappedRequest.getMethod(), path)) {
                log.debug("OR auth: session already authenticated");
                filterChain.doFilter(wrappedRequest, response);
                return;
            }
        }

        // 3. Lookup plugin
        String pluginId = registry.lookupPluginId(wrappedRequest.getMethod(), path);
        if (pluginId == null) {
            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        List<Object> providerObjs = registry.getProviders(pluginId);
        if (providerObjs.isEmpty()) {
            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        // Extract typed providers
        List<IPluginAuthenticationProvider> providers = new ArrayList<>();
        for (Object obj : providerObjs) {
            if (obj instanceof IPluginAuthenticationProvider p) {
                providers.add(p);
            }
        }

        // 4. Execute provider chain via shared engine
        AuthChainExecutor.AuthChainResult result = AuthChainExecutor.execute(
                providers, wrappedRequest, strategy, pluginId);

        // 5. Handle platform-specific result
        if (result instanceof AuthChainExecutor.AuthChainResult.Success success) {
            Authentication auth = success.authentication();
            auth.setAuthenticated(true);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            publishSuccess(success);
            filterChain.doFilter(wrappedRequest, response);
        } else if (result instanceof AuthChainExecutor.AuthChainResult.Challenge challenge) {
            challenge.exception().getHeaders().forEach(response::setHeader);
            response.setStatus(challenge.exception().getStatusCode());
            publishEvent(new PluginAuthenticationFailureEvent(this,
                    challenge.pluginId(), challenge.providerName(),
                    challenge.durationMs(), challenge.exception(), challenge.order()));
        } else if (result instanceof AuthChainExecutor.AuthChainResult.Failure failure) {
            publishChainFailure(failure);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication failed");
        } else {
            filterChain.doFilter(wrappedRequest, response);
        }
    }

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
