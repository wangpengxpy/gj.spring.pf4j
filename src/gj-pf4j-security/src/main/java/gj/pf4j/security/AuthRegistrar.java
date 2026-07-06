/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

import gj.pf4j.GJPluginRequestMappingHandlerMapping;
import gj.pf4j.GJPluginAuthRegistry;
import gj.pf4j.lifecycle.PluginLifecyclePhase;
import gj.pf4j.lifecycle.PluginResourceRegistrar;
import gj.pf4j.webflux.GJPluginWebFluxRequestMappingHandlerMapping;
import gj.pf4j.webflux.GJPluginWebFluxRouterFunctionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Integrates plugin authentication into the plugin lifecycle.
 * <p>
 * Scans plugins for {@link IPluginAuthenticationProvider} beans,
 * classifies them (simple layer vs. advanced layer), resolves path
 * scopes, and registers everything into {@link GJPluginAuthRegistry}.
 * <p>
 * Runs AFTER framework built-in registrars (order = 900), so that
 * controllers are already registered (routing table is populated)
 * when auth providers are scanned.
 */
@Component
public class AuthRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(AuthRegistrar.class);

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH,
                      PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() {
        return 900;
    }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
        String pluginId = pluginCtx.getId();
        ApplicationContext hostCtx = pluginCtx.getParent();

        GJPluginAuthRegistry registry = lookupRegistry(hostCtx);
        PluginAuthenticatedPathRegistry authPathRegistry = lookupAuthPathRegistry(hostCtx);
        if (registry == null) {
            log.debug("[Plugin: {}] GJPluginAuthRegistry not available — skipping auth setup", pluginId);
            return;
        }

        long startTime = System.currentTimeMillis();

        // 1. Collect all paths and @PluginAuthenticated paths
        Map<String, Set<String>> allPaths;
        Map<String, Set<String>> authenticatedPaths;

        if (isWebFlux(hostCtx)) {
            GJPluginWebFluxRequestMappingHandlerMapping handlerMapping =
                    hostCtx.getBean(GJPluginWebFluxRequestMappingHandlerMapping.class);
            GJPluginWebFluxRouterFunctionRegistry routerRegistry =
                    hostCtx.getBean(GJPluginWebFluxRouterFunctionRegistry.class);
            allPaths = merge(handlerMapping.getPluginPaths(pluginId),
                    routerRegistry.getRouterFunctionPaths(pluginId));
            authenticatedPaths = merge(
                    handlerMapping.getPluginAuthenticatedPaths(pluginId),
                    routerRegistry.getRouterFunctionAuthenticatedPaths(pluginId));
        } else {
            GJPluginRequestMappingHandlerMapping handlerMapping =
                    hostCtx.getBeanProvider(GJPluginRequestMappingHandlerMapping.class)
                            .getIfAvailable();
            if (handlerMapping == null) {
                log.debug("[Plugin: {}] No HandlerMapping — non-web app, skipping", pluginId);
                return;
            }
            allPaths = handlerMapping.getPluginPaths(pluginId);
            authenticatedPaths = handlerMapping.getPluginAuthenticatedPaths(pluginId);
        }

        // 2. Register @PluginAuthenticated paths
        if (authPathRegistry != null && !authenticatedPaths.isEmpty()) {
            for (Map.Entry<String, Set<String>> entry : authenticatedPaths.entrySet()) {
                for (String pattern : entry.getValue()) {
                    authPathRegistry.register(pluginId, entry.getKey(), pattern);
                }
            }
        }

        // 3. Get all provider beans
        Map<String, IPluginAuthenticationProvider> allProviders =
                pluginCtx.getBeansOfType(IPluginAuthenticationProvider.class);
        if (allProviders.isEmpty()) {
            log.debug("[Plugin: {}] No IPluginAuthenticationProvider found — "
                    + "using host standard auth", pluginId);
            return;
        }

        // 4. Classify
        AbstractPluginAuthenticationProvider simpleProvider = null;
        List<IPluginAuthenticationProvider> advancedProviders = new ArrayList<>();
        int simpleCount = 0;

        for (IPluginAuthenticationProvider p : allProviders.values()) {
            if (p instanceof AbstractPluginAuthenticationProvider sap) {
                simpleCount++;
                if (simpleProvider == null) {
                    simpleProvider = sap;
                }
            } else {
                advancedProviders.add(p);
            }
        }

        if (simpleCount > 1) {
            log.warn("[Plugin: {}] Multiple AbstractPluginAuthenticationProvider instances ({}). "
                    + "Only the first ({}) will be used.",
                    pluginId, simpleCount, simpleProvider.getClass().getSimpleName());
        }

        // 5. Coexistence check
        if (simpleProvider != null && !advancedProviders.isEmpty()
                && authenticatedPaths.isEmpty()) {
            log.warn("[Plugin: {}] Simple-layer provider ({}) found alongside {} advanced "
                    + "provider(s) without @PluginAuthenticated annotation. "
                    + "Skipping simple-layer to prevent conflict.",
                    pluginId, simpleProvider.getClass().getSimpleName(),
                    advancedProviders.size());
            simpleProvider = null;
        }

        // 6. Simple layer path injection + registration
        if (simpleProvider != null) {
            if (!authenticatedPaths.isEmpty()) {
                simpleProvider.setPluginPaths(authenticatedPaths);
            } else {
                simpleProvider.setPluginPaths(allPaths);
            }
            registry.registerProvider(pluginId, simpleProvider);
        }

        // 7. Register advanced providers
        for (IPluginAuthenticationProvider provider : advancedProviders) {
            registry.registerProvider(pluginId, provider);
        }

        log.info("[Plugin: {}] Auth setup complete in {}ms: {} provider(s) "
                + "(simple={}, advanced={}), {} all routes, {} @PluginAuthenticated routes",
                pluginId, System.currentTimeMillis() - startTime,
                allProviders.size(),
                simpleProvider != null ? 1 : 0, advancedProviders.size(),
                sumPatterns(allPaths), sumPatterns(authenticatedPaths));
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginCtx) {
        String pluginId = pluginCtx.getId();
        ApplicationContext hostCtx = pluginCtx.getParent();

        GJPluginAuthRegistry registry = lookupRegistry(hostCtx);
        if (registry != null) {
            registry.unregister(pluginId);
        }
        PluginAuthenticatedPathRegistry authPathRegistry = lookupAuthPathRegistry(hostCtx);
        if (authPathRegistry != null) {
            authPathRegistry.unregister(pluginId);
        }
    }

    // ──── Helpers ────────────────────────────────────────────────

    private static GJPluginAuthRegistry lookupRegistry(ApplicationContext ctx) {
        ObjectProvider<GJPluginAuthRegistry> provider = ctx.getBeanProvider(GJPluginAuthRegistry.class);
        return provider.getIfAvailable();
    }

    private static PluginAuthenticatedPathRegistry lookupAuthPathRegistry(ApplicationContext ctx) {
        ObjectProvider<PluginAuthenticatedPathRegistry> provider =
                ctx.getBeanProvider(PluginAuthenticatedPathRegistry.class);
        return provider.getIfAvailable();
    }

    private static boolean isWebFlux(ApplicationContext hostCtx) {
        return !hostCtx.getBeansOfType(GJPluginWebFluxRequestMappingHandlerMapping.class).isEmpty();
    }

    private static Map<String, Set<String>> merge(Map<String, Set<String>> a,
                                                   Map<String, Set<String>> b) {
        if (b.isEmpty()) return a;
        if (a.isEmpty()) return b;
        Map<String, Set<String>> result = new LinkedHashMap<>(a);
        for (Map.Entry<String, Set<String>> entry : b.entrySet()) {
            result.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>())
                    .addAll(entry.getValue());
        }
        return result;
    }

    private static int sumPatterns(Map<String, Set<String>> paths) {
        return paths.values().stream().mapToInt(Set::size).sum();
    }
}
