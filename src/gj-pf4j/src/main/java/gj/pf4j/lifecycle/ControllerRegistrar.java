/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.GJPluginContext;
import gj.pf4j.GJPluginRequestMappingHandlerMapping;
import gj.pf4j.anonymous.AnonymousRouteDeclaration;
import gj.pf4j.anonymous.AnonymousPathEntry;
import gj.pf4j.anonymous.PluginAnonymousPathRegistrar;
import gj.pf4j.openapi.GJPluginOpenApiConfig;
import gj.pf4j.openapi.GJPluginOpenApiInfo;
import gj.pf4j.webflux.GJPluginWebFluxRequestMappingHandlerMapping;
import gj.pf4j.webflux.GJPluginWebFluxRouterFunctionRegistry;
import gj.pf4j.webflux.GJRouterFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

class ControllerRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ControllerRegistrar.class);

    private final GJPluginContext pluginContext;
    private final GenericApplicationContext mainAppCtx;

    ControllerRegistrar(GJPluginContext pluginContext, GenericApplicationContext mainAppCtx) {
        this.pluginContext = pluginContext;
        this.mainAppCtx = mainAppCtx;
    }

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() { return 10; }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext ctx) {
        Set<Object> controllers = registerControllers();
        registerPluginOpenApi(controllers);
    }

    @Override
    public void onBeforeContextClose() {
        String pluginId = pluginContext.getPluginId();

        Map<String, GJPluginWebFluxRequestMappingHandlerMapping> webFluxMappings =
                mainAppCtx.getBeansOfType(GJPluginWebFluxRequestMappingHandlerMapping.class);
        if (!webFluxMappings.isEmpty()) {
            webFluxMappings.values().iterator().next().unregisterHandlerMethods(pluginId);
            unregisterRouterFunctions();
        } else {
            Map<String, GJPluginRequestMappingHandlerMapping> mvcMappings =
                    mainAppCtx.getBeansOfType(GJPluginRequestMappingHandlerMapping.class);
            if (!mvcMappings.isEmpty()) {
                mvcMappings.values().iterator().next().unregisterController(pluginId);
            }
        }

        // OpenAPI cleanup
        ((AbstractAutowireCapableBeanFactory) mainAppCtx.getBeanFactory())
                .destroySingleton(GJPluginOpenApiConfig.PLUGIN_SWAGGER_BEAN_PREFIX + pluginId);
        GJPluginOpenApiConfig.unregisterPluginOpenApiBeans(pluginId);
        Object resource = GJPluginOpenApiConfig.findMultipleOpenApiResource(mainAppCtx);
        if (resource != null) {
            try {
                Field groupedOpenApisField =
                        GJPluginOpenApiConfig.getGroupedOpenApisField(resource);
                @SuppressWarnings("unchecked")
                List<GroupedOpenApi> groupedOpenApis =
                        (List<GroupedOpenApi>) groupedOpenApisField.get(resource);
                groupedOpenApis.removeIf(g -> g.getGroup().equals(pluginId));
                resource.getClass().getMethod("afterPropertiesSet").invoke(resource);
            } catch (Exception e) {
                log.warn("[Plugin: {}] Failed to remove OpenAPI group from springdoc", pluginId, e);
            }
        }
    }

    private Set<Object> registerControllers() {
        String pluginId = pluginContext.getPluginId();
        Set<Object> controllers;

        Map<String, GJPluginWebFluxRequestMappingHandlerMapping> webFluxMappings =
                mainAppCtx.getBeansOfType(GJPluginWebFluxRequestMappingHandlerMapping.class);
        if (!webFluxMappings.isEmpty()) {
            GJPluginWebFluxRequestMappingHandlerMapping handlerMapping =
                    webFluxMappings.values().iterator().next();
            controllers = handlerMapping.registerControllers(
                    pluginId, pluginContext.getApplicationContext());
            registerRouterFunctions();
        } else {
            Map<String, GJPluginRequestMappingHandlerMapping> mvcMappings =
                    mainAppCtx.getBeansOfType(GJPluginRequestMappingHandlerMapping.class);
            if (!mvcMappings.isEmpty()) {
                GJPluginRequestMappingHandlerMapping handlerMapping =
                        mvcMappings.values().iterator().next();
                controllers = handlerMapping.registerControllers(pluginContext);
            } else {
                log.debug("[Plugin: {}] No HandlerMapping found (non-web application), " +
                        "skipping controller registration.", pluginId);
                return Collections.emptySet();
            }
        }
        return controllers;
    }

    private void registerPluginOpenApi(Set<Object> controllers) {
        if (controllers.isEmpty()) {
            return;
        }
        GJPluginOpenApiInfo pluginOpenApiInfo = new GJPluginOpenApiInfo();
        pluginOpenApiInfo.setGroupName(pluginContext.getPluginId());
        List<String> controllerPackages = new ArrayList<>();
        List<Class<?>> controllerClasses = new ArrayList<>();
        for (Object controller : controllers) {
            controllerPackages.add(controller.getClass().getPackageName());
            controllerClasses.add(controller.getClass());
        }
        pluginOpenApiInfo.setControllerPackages(
                controllerPackages.stream().distinct().collect(Collectors.toList()));
        pluginOpenApiInfo.setControllerClasses(controllerClasses);
        GJPluginOpenApiConfig.registerPluginOpenApiBeans(
                this.mainAppCtx, pluginOpenApiInfo);
    }

    private void registerRouterFunctions() {
        Map<String, RouterFunction> routerFunctions =
                pluginContext.getApplicationContext()
                        .getBeansOfType(RouterFunction.class);
        if (routerFunctions.isEmpty()) {
            return;
        }
        GJPluginWebFluxRouterFunctionRegistry registry =
                mainAppCtx.getBean(GJPluginWebFluxRouterFunctionRegistry.class);
        PluginAnonymousPathRegistrar registrar =
                mainAppCtx.getBean(PluginAnonymousPathRegistrar.class);
        List<RouterFunction<ServerResponse>> functions = new ArrayList<>();

        for (RouterFunction rf : routerFunctions.values()) {
            RouterFunction<ServerResponse> casted = (RouterFunction<ServerResponse>) rf;
            if (rf instanceof GJRouterFunctions.AnnotatedRouterFunction arf) {
                for (AnonymousRouteDeclaration decl : arf.getDeclarations()) {
                    registrar.register(pluginContext.getPluginId(),
                            new AnonymousPathEntry(
                                    pluginContext.getPluginId(), decl.pathPattern(),
                                    decl.httpMethod(), null, null, decl.reason(),
                                    java.time.LocalDateTime.now()));
                }
                functions.add(arf.getDelegate());
            } else {
                functions.add(casted);
            }
        }
        registry.register(functions);
    }

    private void unregisterRouterFunctions() {
        Map<String, GJPluginWebFluxRouterFunctionRegistry> registries =
                mainAppCtx.getBeansOfType(GJPluginWebFluxRouterFunctionRegistry.class);
        if (registries.isEmpty()) {
            return;
        }
        Map<String, RouterFunction> current = pluginContext.getApplicationContext()
                .getBeansOfType(RouterFunction.class);
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

        // Clean up anonymous paths
        PluginAnonymousPathRegistrar registrar =
                mainAppCtx.getBean(PluginAnonymousPathRegistrar.class);
        registrar.unregisterByPlugin(pluginContext.getPluginId());
    }
}
