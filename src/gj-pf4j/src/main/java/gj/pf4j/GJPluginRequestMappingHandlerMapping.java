/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class GJPluginRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    private static final Logger log = LoggerFactory.getLogger(GJPluginRequestMappingHandlerMapping.class);

    private final MultiValueMap<String, RequestMappingInfo> pluginMappingInfo = new LinkedMultiValueMap<>();

    @Override
    public void detectHandlerMethods(Object controller) {
    }

    @Override
    protected void initHandlerMethods() {
    }

    public Set<Object> registerControllers(GJPluginContext pluginContext) {
        String pluginId = pluginContext.getPluginId();
        long startTime = System.currentTimeMillis();
        log.debug("Starting to register controllers for plugin: {}", pluginId);

        try {
            Set<Object> controllers = getControllerBeans(pluginContext);
            List<String> controllerNames = controllers.stream()
                    .map(c -> c.getClass().getSimpleName())
                    .collect(Collectors.toList());

            log.info("Found {} controllers in plugin {}: {}",
                    controllers.size(), pluginId, controllerNames);

            for (Object controller : controllers) {
                registerController(pluginId, controller);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully registered {} controllers for plugin: {} (took {} ms)",
                    controllers.size(), pluginId, duration);
            return controllers;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to register controllers for plugin: {} (took {} ms)", pluginId, duration, e);
            throw e;
        }
    }

    private void registerController(String pluginId, Object controller) {
        String controllerClassName = controller.getClass().getName();
        long startTime = System.currentTimeMillis();

        try {
            log.info("Registering controller: {} for plugin: {}", controllerClassName, pluginId);

            // 1. Get the actual type of the controller (non-proxy class)
            Class<?> handlerType = controller.getClass();
            Class<?> userType = ClassUtils.getUserClass(handlerType);
            // 2. Explicitly declare MetadataLookup
            MethodIntrospector.MetadataLookup<RequestMappingInfo> metadataLookup =
                    method -> super.getMappingForMethod(method, handlerType);
            // 3. Resolve routes for all methods (including methods without annotations)
            Map<Method, RequestMappingInfo> methods = MethodIntrospector.selectMethods(
                    userType,
                    metadataLookup
            );
            // Register routes directly (the handler is a Bean instance from the plugin context)
            methods.forEach((method, mapping) -> {
                // Core 1: Get the actual invocable specific route method of the controller
                Method invocableMethod = AopUtils.selectInvocableMethod(method, userType);
                // Core 2: Call registerHandlerMethod directly and pass in the plugin's Bean instance
                super.registerHandlerMethod(controller, invocableMethod, mapping);
                pluginMappingInfo.add(pluginId, mapping);
            });

            long duration = System.currentTimeMillis() - startTime;
            log.debug("Registered controller: {} for plugin: {} (took {} ms)",
                    controllerClassName, pluginId, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to register controller: {} for plugin: {} (took {} ms)",
                    controllerClassName, pluginId, duration, e);
            throw e;
        }
    }

    private Set<Object> getControllerBeans(GJPluginContext pluginContext) {
        ApplicationContext applicationContext = pluginContext.getApplicationContext();
        Set<Object> beans = new LinkedHashSet<>();

        Map<String, Object> controllerBeans = applicationContext.getBeansWithAnnotation(Controller.class);
        Map<String, Object> restControllerBeans = applicationContext.getBeansWithAnnotation(RestController.class);

        beans.addAll(controllerBeans.values());
        beans.addAll(restControllerBeans.values());

        if (log.isTraceEnabled()) {
            List<String> names = beans.stream()
                    .map(b -> b.getClass().getSimpleName())
                    .collect(Collectors.toList());
            log.debug("Scanned {} controller beans: {}", beans.size(), names);
        }

        return beans;
    }

    void unregisterController(String pluginId) {
        if (!pluginMappingInfo.containsKey(pluginId)) {
            return;
        }
        List<RequestMappingInfo> mappings = pluginMappingInfo.remove(pluginId);
        mappings.forEach(this::unregisterMapping);
        log.debug("Unregistered {} old routes for plugin: {}", mappings.size(), pluginId);
    }
}
