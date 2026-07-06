/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import gj.pf4j.core.AllowAnonymous;
import gj.pf4j.core.AnonymousPathEntry;
import gj.pf4j.core.PluginAnonymousPathRegistrar;
import gj.pf4j.core.PluginAuthenticated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class GJPluginRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    private static final Logger log = LoggerFactory.getLogger(GJPluginRequestMappingHandlerMapping.class);

    private final MultiValueMap<String, RequestMappingInfo> pluginMappingInfo = new LinkedMultiValueMap<>();

    /** pluginId → (HTTP method → path patterns) — all plugin routes */
    private final Map<String, Map<String, Set<String>>> pluginPaths = new LinkedHashMap<>();

    /** pluginId → (HTTP method → path patterns) — only @PluginAuthenticated routes */
    private final Map<String, Map<String, Set<String>>> pluginAuthenticatedPaths = new LinkedHashMap<>();

    public MultiValueMap<String, RequestMappingInfo> getPluginMappingInfo() {
        return pluginMappingInfo;
    }

    private PluginAnonymousPathRegistrar anonymousPathRegistrar;

    public void setAnonymousPathRegistry(PluginAnonymousPathRegistrar anonymousPathRegistrar) {
        this.anonymousPathRegistrar = anonymousPathRegistrar;
    }

    @Override
    public void detectHandlerMethods(Object controller) {
    }

    @Override
    protected void initHandlerMethods() {
    }

    public Set<Object> registerControllers(String pluginId, ApplicationContext ctx) {
        long startTime = System.currentTimeMillis();
        log.debug("Starting to register controllers for plugin: {}", pluginId);

        try {
            Set<Object> controllers = getControllerBeans(ctx);
            List<String> controllerNames = controllers.stream()
                    .map(c -> c.getClass().getSimpleName())
                    .collect(Collectors.toList());

            log.info("Found {} controllers in plugin {}: {}",
                    controllers.size(), pluginId, controllerNames);

            int anonymousCount = 0;
            for (Object controller : controllers) {
                anonymousCount += registerController(pluginId, controller);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully registered {} controllers for plugin: {} (took {} ms), " +
                    "including {} anonymous endpoints",
                    controllers.size(), pluginId, duration, anonymousCount);
            return controllers;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to register controllers for plugin: {} (took {} ms)", pluginId, duration, e);
            throw e;
        }
    }

    private int registerController(String pluginId, Object controller) {
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
            // Check class-level annotations
            AllowAnonymous classAnno = userType.getAnnotation(AllowAnonymous.class);
            PluginAuthenticated classAuthAnno = userType.getAnnotation(PluginAuthenticated.class);

            // Track anonymous endpoint count for this controller
            int[] anonymousCount = {0};

            // Register routes directly (the handler is a Bean instance from the plugin context)
            methods.forEach((method, mapping) -> {
                // Core 1: Get the actual invocable specific route method of the controller
                Method invocableMethod = AopUtils.selectInvocableMethod(method, userType);
                // Core 2: Call registerHandlerMethod directly and pass in the plugin's Bean instance
                super.registerHandlerMethod(controller, invocableMethod, mapping);
                pluginMappingInfo.add(pluginId, mapping);

                // Collect all route patterns for auth routing
                collectPluginPaths(pluginId, mapping);

                // Collect @PluginAuthenticated paths
                PluginAuthenticated methodAuthAnno = method.getAnnotation(PluginAuthenticated.class);
                if (methodAuthAnno != null || classAuthAnno != null) {
                    // Exclude paths that are also @AllowAnonymous
                    if (!(method.getAnnotation(AllowAnonymous.class) != null || classAnno != null)) {
                        collectPluginAuthenticatedPaths(pluginId, mapping);
                    }
                }

                // Core 3: Scan @AllowAnonymous and register to registry
                if (anonymousPathRegistrar != null) {
                    AllowAnonymous methodAnno = method.getAnnotation(AllowAnonymous.class);
                    if (methodAnno != null || classAnno != null) {
                        String reason = methodAnno != null && !methodAnno.reason().isEmpty()
                                ? methodAnno.reason()
                                : (classAnno != null ? classAnno.reason() : "");
                        int count = registerAnonymousPaths(pluginId, controllerClassName,
                                method.getName(), mapping, reason);
                        anonymousCount[0] += count;
                    }
                }
            });

            long duration = System.currentTimeMillis() - startTime;
            log.debug("Registered controller: {} for plugin: {} (took {} ms), " +
                    "including {} anonymous endpoints",
                    controllerClassName, pluginId, duration, anonymousCount[0]);

            return anonymousCount[0];

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to register controller: {} for plugin: {} (took {} ms)",
                    controllerClassName, pluginId, duration, e);
            throw e;
        }
    }

    private int registerAnonymousPaths(String pluginId, String controllerClass,
                                        String methodName, RequestMappingInfo mapping,
                                        String reason) {
        Set<String> patterns = mapping.getPatternValues();
        if (patterns == null || patterns.isEmpty()) {
            return 0;
        }
        Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
        int count = 0;
        for (String pattern : patterns) {
            if (methods.isEmpty()) {
                anonymousPathRegistrar.register(pluginId, new AnonymousPathEntry(
                        pluginId, pattern, "*",
                        controllerClass, methodName, reason, LocalDateTime.now()));
                count++;
            } else {
                for (RequestMethod httpMethod : methods) {
                    anonymousPathRegistrar.register(pluginId, new AnonymousPathEntry(
                            pluginId, pattern, httpMethod.name(),
                            controllerClass, methodName, reason, LocalDateTime.now()));
                    count++;
                }
            }
        }
        return count;
    }

    private Set<Object> getControllerBeans(ApplicationContext ctx) {
        Set<Object> beans = new LinkedHashSet<>();

        Map<String, Object> controllerBeans = ctx.getBeansWithAnnotation(Controller.class);
        Map<String, Object> restControllerBeans = ctx.getBeansWithAnnotation(RestController.class);

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

    public void unregisterController(String pluginId) {
        if (anonymousPathRegistrar != null) {
            anonymousPathRegistrar.unregister(pluginId);
        }
        pluginPaths.remove(pluginId);
        pluginAuthenticatedPaths.remove(pluginId);
        List<RequestMappingInfo> mappings = pluginMappingInfo.remove(pluginId);
        if (mappings == null) {
            return;
        }
        mappings.forEach(this::unregisterMapping);
        log.debug("Unregistered {} routes for plugin: {}", mappings.size(), pluginId);
    }

    // ──── Path collection for auth routing ───────────────────────

    public Map<String, Set<String>> getPluginPaths(String pluginId) {
        return pluginPaths.getOrDefault(pluginId, Map.of());
    }

    public Map<String, Set<String>> getPluginAuthenticatedPaths(String pluginId) {
        return pluginAuthenticatedPaths.getOrDefault(pluginId, Map.of());
    }

    private void collectPluginPaths(String pluginId, RequestMappingInfo mapping) {
        Set<String> patterns = mapping.getPatternValues();
        if (patterns == null || patterns.isEmpty()) return;
        Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
        Map<String, Set<String>> pluginMap = pluginPaths.computeIfAbsent(pluginId,
                k -> new LinkedHashMap<>());
        if (methods.isEmpty()) {
            for (String m : ALL_METHODS) {
                pluginMap.computeIfAbsent(m, k -> new LinkedHashSet<>()).addAll(patterns);
            }
        } else {
            for (RequestMethod rm : methods) {
                pluginMap.computeIfAbsent(rm.name(), k -> new LinkedHashSet<>()).addAll(patterns);
            }
        }
    }

    private void collectPluginAuthenticatedPaths(String pluginId, RequestMappingInfo mapping) {
        Set<String> patterns = mapping.getPatternValues();
        if (patterns == null || patterns.isEmpty()) return;
        Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
        Map<String, Set<String>> pluginMap = pluginAuthenticatedPaths.computeIfAbsent(pluginId,
                k -> new LinkedHashMap<>());
        if (methods.isEmpty()) {
            for (String m : ALL_METHODS) {
                pluginMap.computeIfAbsent(m, k -> new LinkedHashSet<>()).addAll(patterns);
            }
        } else {
            for (RequestMethod rm : methods) {
                pluginMap.computeIfAbsent(rm.name(), k -> new LinkedHashSet<>()).addAll(patterns);
            }
        }
    }

    private static final List<String> ALL_METHODS = List.of("GET", "POST", "PUT", "DELETE", "PATCH");
}
