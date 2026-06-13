/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.webflux;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodIntrospector;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.result.method.RequestMappingInfo;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class GJPluginWebFluxRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    private final MultiValueMap<String, RequestMappingInfo> pluginRequestMappingInfo =
            new LinkedMultiValueMap<>();

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
            Map<Method, RequestMappingInfo> methods = MethodIntrospector.selectMethods(userType,
                    (MethodIntrospector.MetadataLookup<RequestMappingInfo>)
                            method -> super.getMappingForMethod(method, handlerType));
            log.debug(formatMappings(userType, methods));
            methods.forEach((method, mapping) -> {
                Method invocableMethod = AopUtils.selectInvocableMethod(method, userType);
                registerHandlerMethod(handler, invocableMethod, mapping);
                pluginRequestMappingInfo.add(pluginId, mapping);
            });
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
        if (!StringUtils.hasText(pluginId)) {
            return;
        }
        if (!pluginRequestMappingInfo.containsKey(pluginId)) {
            return;
        }
        pluginRequestMappingInfo.remove(pluginId).forEach(this::unregisterMapping);
    }
}
