/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.GJPluginRequestMappingHandlerMapping;
import gj.pf4j.GJPluginAuthRegistry;
import gj.pf4j.core.AnonymousRouteDeclaration;
import gj.pf4j.webflux.GJPluginWebFluxRequestMappingHandlerMapping;
import gj.pf4j.webflux.GJPluginWebFluxRouterFunctionRegistry;
import gj.pf4j.webflux.GJRouterFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.*;
import java.util.stream.Collectors;

class ControllerRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ControllerRegistrar.class);

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() { return 10; }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
        String pluginId = pluginCtx.getId();
        ApplicationContext hostCtx = pluginCtx.getParent();
        GJPluginAuthRegistry authRegistry = lookupAuthRegistry(hostCtx);

        Map<String, GJPluginWebFluxRequestMappingHandlerMapping> webFluxMappings =
                hostCtx.getBeansOfType(GJPluginWebFluxRequestMappingHandlerMapping.class);
        if (!webFluxMappings.isEmpty()) {
            GJPluginWebFluxRequestMappingHandlerMapping handlerMapping =
                    webFluxMappings.values().iterator().next();
            handlerMapping.registerControllers(pluginId, pluginCtx);
            registerWebFluxPluginRoutes(authRegistry, pluginId, handlerMapping);
            registerRouterFunctions(authRegistry, pluginId, pluginCtx, hostCtx);
            return;
        }

        Map<String, GJPluginRequestMappingHandlerMapping> mvcMappings =
                hostCtx.getBeansOfType(GJPluginRequestMappingHandlerMapping.class);
        if (!mvcMappings.isEmpty()) {
            GJPluginRequestMappingHandlerMapping handlerMapping =
                    mvcMappings.values().iterator().next();
            handlerMapping.registerControllers(pluginId, pluginCtx);
            registerMvcPluginRoutes(authRegistry, pluginId, handlerMapping);
        } else {
            log.debug("[Plugin: {}] No HandlerMapping found (non-web application), " +
                    "skipping controller registration.", pluginId);
        }
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginCtx) {
        String pluginId = pluginCtx.getId();
        ApplicationContext hostCtx = pluginCtx.getParent();

        Map<String, GJPluginWebFluxRequestMappingHandlerMapping> webFluxMappings =
                hostCtx.getBeansOfType(GJPluginWebFluxRequestMappingHandlerMapping.class);
        if (!webFluxMappings.isEmpty()) {
            webFluxMappings.values().iterator().next().unregisterHandlerMethods(pluginId);
            unregisterRouterFunctions(pluginCtx, hostCtx);
            return;
        }

        Map<String, GJPluginRequestMappingHandlerMapping> mvcMappings =
                hostCtx.getBeansOfType(GJPluginRequestMappingHandlerMapping.class);
        if (!mvcMappings.isEmpty()) {
            mvcMappings.values().iterator().next().unregisterController(pluginId);
        }
    }

    private static GJPluginAuthRegistry lookupAuthRegistry(ApplicationContext hostCtx) {
        ObjectProvider<GJPluginAuthRegistry> provider =
                hostCtx.getBeanProvider(GJPluginAuthRegistry.class);
        return provider.getIfAvailable();
    }

    private void registerRouterFunctions(GJPluginAuthRegistry authRegistry,
                                          String pluginId,
                                          AnnotationConfigApplicationContext pluginCtx,
                                          ApplicationContext hostCtx) {
        Map<String, RouterFunction> routerFunctions =
                pluginCtx.getBeansOfType(RouterFunction.class);
        if (routerFunctions.isEmpty()) {
            return;
        }
        GJPluginWebFluxRouterFunctionRegistry registry =
                hostCtx.getBean(GJPluginWebFluxRouterFunctionRegistry.class);
        List<RouterFunction<ServerResponse>> functions = new ArrayList<>();
        for (RouterFunction rf : routerFunctions.values()) {
            RouterFunction<ServerResponse> casted = (RouterFunction<ServerResponse>) rf;
            if (rf instanceof GJRouterFunctions.AnnotatedRouterFunction arf) {
                functions.add(arf.getDelegate());
            } else {
                functions.add(casted);
            }
        }
        registry.register(pluginId, functions);

        if (authRegistry != null) {
            Map<String, Set<String>> methodPatterns = new LinkedHashMap<>();
            for (RouterFunction rf : routerFunctions.values()) {
                if (rf instanceof GJRouterFunctions.AnnotatedRouterFunction arf) {
                    for (AnonymousRouteDeclaration d : arf.getDeclarations()) {
                        methodPatterns.computeIfAbsent(
                                d.httpMethod().toUpperCase(),
                                k -> new LinkedHashSet<>()).add(d.pathPattern());
                    }
                }
            }
            if (!methodPatterns.isEmpty()) {
                authRegistry.registerRoutes(pluginId, methodPatterns);
            }
        }
    }

    private void unregisterRouterFunctions(AnnotationConfigApplicationContext pluginCtx,
                                            ApplicationContext hostCtx) {
        Map<String, GJPluginWebFluxRouterFunctionRegistry> registries =
                hostCtx.getBeansOfType(GJPluginWebFluxRouterFunctionRegistry.class);
        if (registries.isEmpty()) {
            return;
        }
        Map<String, RouterFunction> current = pluginCtx.getBeansOfType(RouterFunction.class);
        List<RouterFunction<ServerResponse>> functions = new ArrayList<>();
        for (RouterFunction rf : current.values()) {
            RouterFunction<ServerResponse> casted = (RouterFunction<ServerResponse>) rf;
            if (rf instanceof GJRouterFunctions.AnnotatedRouterFunction arf) {
                functions.add(arf.getDelegate());
            } else {
                functions.add(casted);
            }
        }
        String pluginId = pluginCtx.getId();
        registries.values().iterator().next().unregister(pluginId, functions);
    }

    private void registerMvcPluginRoutes(GJPluginAuthRegistry authRegistry,
                                          String pluginId,
                                          GJPluginRequestMappingHandlerMapping handlerMapping) {
        if (authRegistry == null) return;
        var mappings = handlerMapping.getPluginMappingInfo().get(pluginId);
        if (mappings == null || mappings.isEmpty()) return;
        Map<String, Set<String>> methodPatterns = new LinkedHashMap<>();
        for (var mapping : mappings) {
            Set<String> patterns = mapping.getPatternValues();
            if (patterns == null || patterns.isEmpty()) continue;
            Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
            if (methods.isEmpty()) {
                for (String m : ALL_METHODS) {
                    methodPatterns.computeIfAbsent(m, k -> new LinkedHashSet<>())
                            .addAll(patterns);
                }
            } else {
                for (RequestMethod rm : methods) {
                    methodPatterns.computeIfAbsent(rm.name(), k -> new LinkedHashSet<>())
                            .addAll(patterns);
                }
            }
        }
        if (!methodPatterns.isEmpty()) {
            authRegistry.registerRoutes(pluginId, methodPatterns);
        }
    }

    private void registerWebFluxPluginRoutes(GJPluginAuthRegistry authRegistry,
                                              String pluginId,
                                              GJPluginWebFluxRequestMappingHandlerMapping handlerMapping) {
        if (authRegistry == null) return;
        var mappings = handlerMapping.getPluginRequestMappingInfo().get(pluginId);
        if (mappings == null || mappings.isEmpty()) return;
        Map<String, Set<String>> methodPatterns = new LinkedHashMap<>();
        for (var mapping : mappings) {
            Set<String> patterns = mapping.getPatternsCondition().getPatterns().stream()
                    .map(pp -> pp.getPatternString()).collect(Collectors.toSet());
            if (patterns.isEmpty()) continue;
            Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
            if (methods.isEmpty()) {
                for (String m : ALL_METHODS) {
                    methodPatterns.computeIfAbsent(m, k -> new LinkedHashSet<>())
                            .addAll(patterns);
                }
            } else {
                for (RequestMethod rm : methods) {
                    methodPatterns.computeIfAbsent(rm.name(), k -> new LinkedHashSet<>())
                            .addAll(patterns);
                }
            }
        }
        if (!methodPatterns.isEmpty()) {
            authRegistry.registerRoutes(pluginId, methodPatterns);
        }
    }

    private static final List<String> ALL_METHODS =
            List.of("GET", "POST", "PUT", "DELETE", "PATCH");
}
