/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.webflux;

import gj.pf4j.core.AllowAnonymous;
import gj.pf4j.core.AnonymousPathEntry;
import gj.pf4j.core.PluginAnonymousPathRegistrar;
import gj.pf4j.core.PluginAuthenticated;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class GJPluginWebFluxRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    private final MultiValueMap<String, RequestMappingInfo> pluginRequestMappingInfo =
            new LinkedMultiValueMap<>();

    /** pluginId → (HTTP method → path patterns) — all plugin routes */
    private final Map<String, Map<String, Set<String>>> pluginPaths = new LinkedHashMap<>();

    /** pluginId → (HTTP method → path patterns) — only @PluginAuthenticated routes */
    private final Map<String, Map<String, Set<String>>> pluginAuthenticatedPaths = new LinkedHashMap<>();

    public MultiValueMap<String, RequestMappingInfo> getPluginRequestMappingInfo() {
        return pluginRequestMappingInfo;
    }

    private PluginAnonymousPathRegistrar anonymousPathRegistrar;

    public void setAnonymousPathRegistry(PluginAnonymousPathRegistrar anonymousPathRegistrar) {
        this.anonymousPathRegistrar = anonymousPathRegistrar;
    }

    @Override
    protected void initHandlerMethods() {
    }

    @Override
    protected void detectHandlerMethods(Object handler) {
    }

    public Set<Object> registerControllers(String pluginId, ApplicationContext pluginAppContext) {
        long startTime = System.currentTimeMillis();
        log.debug("Starting to register WebFlux controllers for plugin: {}", pluginId);

        try {
            Set<Object> controllers = getControllerBeans(pluginAppContext);
            List<String> controllerNames = controllers.stream()
                    .map(c -> c.getClass().getSimpleName())
                    .collect(Collectors.toList());

            log.info("Found {} WebFlux controllers in plugin {}: {}",
                    controllers.size(), pluginId, controllerNames);

            for (Object controller : controllers) {
                registerHandlerMethods(pluginId, controller);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully registered {} WebFlux controllers for plugin: {} (took {} ms)",
                    controllers.size(), pluginId, duration);
            return controllers;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to register WebFlux controllers for plugin: {} (took {} ms)",
                    pluginId, duration, e);
            throw e;
        }
    }

    public void registerHandlerMethods(String pluginId, Object handler) {
        Class<?> handlerType = (handler instanceof String beanName
                ? obtainApplicationContext().getType(beanName) : handler.getClass());

        if (handlerType != null) {
            final Class<?> userType = ClassUtils.getUserClass(handlerType);
            // Check class-level annotations
            AllowAnonymous classAnno = userType.getAnnotation(AllowAnonymous.class);
            PluginAuthenticated classAuthAnno = userType.getAnnotation(PluginAuthenticated.class);

            Map<Method, RequestMappingInfo> methods = MethodIntrospector.selectMethods(userType,
                    (MethodIntrospector.MetadataLookup<RequestMappingInfo>)
                            method -> super.getMappingForMethod(method, handlerType));
            log.debug(formatMappings(userType, methods));
            methods.forEach((method, mapping) -> {
                Method invocableMethod = AopUtils.selectInvocableMethod(method, userType);
                registerHandlerMethod(handler, invocableMethod, mapping);
                pluginRequestMappingInfo.add(pluginId, mapping);

                // Collect all route patterns for auth routing
                collectPluginPaths(pluginId, mapping);

                // Collect @PluginAuthenticated paths
                PluginAuthenticated methodAuthAnno = method.getAnnotation(PluginAuthenticated.class);
                if (methodAuthAnno != null || classAuthAnno != null) {
                    if (!(method.getAnnotation(AllowAnonymous.class) != null || classAnno != null)) {
                        collectPluginAuthenticatedPaths(pluginId, mapping);
                    }
                }

                if (anonymousPathRegistrar != null) {
                    AllowAnonymous methodAnno = method.getAnnotation(AllowAnonymous.class);
                    if (methodAnno != null || classAnno != null) {
                        String reason = methodAnno != null && !methodAnno.reason().isEmpty()
                                ? methodAnno.reason()
                                : (classAnno != null ? classAnno.reason() : "");
                        registerAnonymousPaths(pluginId, handlerType.getName(),
                                method.getName(), mapping, reason);
                    }
                }
            });
        }
    }

    private void registerAnonymousPaths(String pluginId, String controllerClass,
                                         String methodName, RequestMappingInfo mapping,
                                         String reason) {
        if (mapping.getPatternsCondition() == null) {
            return;
        }
        var pathPatterns = mapping.getPatternsCondition().getPatterns();
        if (pathPatterns == null || pathPatterns.isEmpty()) {
            return;
        }
        List<String> patterns = pathPatterns.stream()
                .map(pp -> pp.getPatternString())
                .toList();
        Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
        for (String pattern : patterns) {
            if (methods.isEmpty()) {
                anonymousPathRegistrar.register(pluginId, new AnonymousPathEntry(
                        pluginId, pattern, "*",
                        controllerClass, methodName, reason, LocalDateTime.now()));
            } else {
                for (var httpMethod : methods) {
                    anonymousPathRegistrar.register(pluginId, new AnonymousPathEntry(
                            pluginId, pattern, httpMethod.name(),
                            controllerClass, methodName, reason, LocalDateTime.now()));
                }
            }
        }
    }

    private Set<Object> getControllerBeans(ApplicationContext applicationContext) {
        Set<Object> beans = new LinkedHashSet<>();

        Map<String, Object> controllerBeans =
                applicationContext.getBeansWithAnnotation(Controller.class);
        Map<String, Object> restControllerBeans =
                applicationContext.getBeansWithAnnotation(RestController.class);

        beans.addAll(controllerBeans.values());
        beans.addAll(restControllerBeans.values());

        if (log.isTraceEnabled()) {
            List<String> names = beans.stream()
                    .map(b -> b.getClass().getSimpleName())
                    .collect(Collectors.toList());
            log.debug("Scanned {} WebFlux controller beans: {}", beans.size(), names);
        }
        return beans;
    }

    private String formatMappings(Class<?> userType, Map<Method, RequestMappingInfo> methods) {
        String packageName = ClassUtils.getPackageName(userType);
        String formattedType = (StringUtils.hasText(packageName)
                ? Arrays.stream(packageName.split("\\."))
                .map(packageSegment -> packageSegment.substring(0, 1))
                .collect(Collectors.joining(".", "", "." + userType.getSimpleName())) :
                userType.getSimpleName());
        Function<Method, String> methodFormatter =
                method -> Arrays.stream(method.getParameterTypes())
                        .map(Class::getSimpleName)
                        .collect(Collectors.joining(",", "(", ")"));
        return methods.entrySet().stream()
                .map(e -> {
                    Method method = e.getKey();
                    return e.getValue() + ": " + method.getName() + methodFormatter.apply(method);
                })
                .collect(Collectors.joining("\n\t", "\n\t" + formattedType + ":" + "\n\t", ""));
    }

    public void unregisterHandlerMethods(String pluginId) {
        if (anonymousPathRegistrar != null) {
            anonymousPathRegistrar.unregister(pluginId);
        }
        pluginPaths.remove(pluginId);
        pluginAuthenticatedPaths.remove(pluginId);
        if (!StringUtils.hasText(pluginId)) {
            return;
        }
        List<RequestMappingInfo> mappings = pluginRequestMappingInfo.remove(pluginId);
        if (mappings == null) {
            return;
        }
        mappings.forEach(this::unregisterMapping);
        log.debug("Unregistered {} WebFlux routes for plugin: {}", mappings.size(), pluginId);
    }

    // ──── Path collection for auth routing ───────────────────────

    public Map<String, Set<String>> getPluginPaths(String pluginId) {
        return pluginPaths.getOrDefault(pluginId, Map.of());
    }

    public Map<String, Set<String>> getPluginAuthenticatedPaths(String pluginId) {
        return pluginAuthenticatedPaths.getOrDefault(pluginId, Map.of());
    }

    private void collectPluginPaths(String pluginId, RequestMappingInfo mapping) {
        Set<String> patterns = mapping.getPatternsCondition().getPatterns().stream()
                .map(p -> p.getPatternString()).collect(Collectors.toSet());
        if (patterns.isEmpty()) return;
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
        Set<String> patterns = mapping.getPatternsCondition().getPatterns().stream()
                .map(p -> p.getPatternString()).collect(Collectors.toSet());
        if (patterns.isEmpty()) return;
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
