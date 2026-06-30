/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.GJPluginRequestMappingHandlerMapping;
import gj.pf4j.webflux.GJPluginWebFluxRequestMappingHandlerMapping;
import gj.pf4j.webflux.GJPluginWebFluxRouterFunctionRegistry;
import gj.pf4j.webflux.GJRouterFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.*;

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

        Map<String, GJPluginWebFluxRequestMappingHandlerMapping> webFluxMappings =
                hostCtx.getBeansOfType(GJPluginWebFluxRequestMappingHandlerMapping.class);
        if (!webFluxMappings.isEmpty()) {
            GJPluginWebFluxRequestMappingHandlerMapping handlerMapping =
                    webFluxMappings.values().iterator().next();
            handlerMapping.registerControllers(pluginId, pluginCtx);
            registerRouterFunctions(pluginCtx, hostCtx);
            return;
        }

        Map<String, GJPluginRequestMappingHandlerMapping> mvcMappings =
                hostCtx.getBeansOfType(GJPluginRequestMappingHandlerMapping.class);
        if (!mvcMappings.isEmpty()) {
            GJPluginRequestMappingHandlerMapping handlerMapping =
                    mvcMappings.values().iterator().next();
            handlerMapping.registerControllers(pluginId, pluginCtx);
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

    private void registerRouterFunctions(AnnotationConfigApplicationContext pluginCtx,
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
        registry.register(functions);
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
        registries.values().iterator().next().unregister(functions);
    }
}
