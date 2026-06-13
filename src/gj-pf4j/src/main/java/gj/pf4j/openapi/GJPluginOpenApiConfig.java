/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.openapi;

import org.springdoc.api.AbstractOpenApiResource;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GJPluginOpenApiConfig {
    public static final String PLUGIN_SWAGGER_BEAN_PREFIX = "pluginGroupedOpenApi-";

    private static final Map<String, List<Class<?>>> PLUGIN_CONTROLLER_CLASSES = new HashMap<>();

    public static void registerPluginOpenApiBeans(ApplicationContext applicationContext,
                                                  GJPluginOpenApiInfo pluginSwaggerInfo) {
        String groupName = pluginSwaggerInfo.getGroupName().trim().toLowerCase();
        if (groupName.isEmpty()) {
            return;
        }
        String beanName = PLUGIN_SWAGGER_BEAN_PREFIX + groupName;

        GroupedOpenApi groupedOpenApi = GroupedOpenApi.builder()
                .group(groupName)
                .displayName(groupName)
                .packagesToScan(pluginSwaggerInfo.getControllerPackages().toArray(new String[0]))
                .build();
        ((GenericApplicationContext) applicationContext).getBeanFactory()
                .registerSingleton(beanName, groupedOpenApi);

        List<Class<?>> controllerClasses = pluginSwaggerInfo.getControllerClasses();
        if (controllerClasses != null && !controllerClasses.isEmpty()) {
            AbstractOpenApiResource.addRestControllers(
                    controllerClasses.toArray(new Class<?>[0]));
            PLUGIN_CONTROLLER_CLASSES.put(groupName, controllerClasses);
        }

        Object resource = findMultipleOpenApiResource(applicationContext);
        if (resource == null) {
            throw new IllegalStateException(
                    "MultipleOpenApiResource not found in application context. " +
                    "Ensure application default springdoc.group-configs is configured in application.yml");
        }
        try {
            Field groupedOpenApisField = getGroupedOpenApisField(resource);
            @SuppressWarnings("unchecked")
            List<GroupedOpenApi> groupedOpenApis =
                    (List<GroupedOpenApi>) groupedOpenApisField.get(resource);
            groupedOpenApis.add(groupedOpenApi);
            resource.getClass().getMethod("afterPropertiesSet").invoke(resource);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to inject plugin OpenAPI group into springdoc: " + groupName, e);
        }
    }

    public static Object findMultipleOpenApiResource(ApplicationContext ctx) {
        try {
            Class<?> mvcClass = Class.forName("org.springdoc.webmvc.api.MultipleOpenApiResource");
            Map<String, ?> beans = ctx.getBeansOfType(mvcClass);
            if (!beans.isEmpty()) {
                return beans.values().iterator().next();
            }
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class<?> webfluxClass = Class.forName("org.springdoc.webflux.api.MultipleOpenApiResource");
            Map<String, ?> beans = ctx.getBeansOfType(webfluxClass);
            if (!beans.isEmpty()) {
                return beans.values().iterator().next();
            }
        } catch (ClassNotFoundException ignored) {
        }
        return null;
    }

    public static Field getGroupedOpenApisField(Object resource) {
        Class<?> clazz = resource.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField("groupedOpenApis");
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new IllegalArgumentException(
                "groupedOpenApis field not found on " + resource.getClass().getName() + " or its superclasses");
    }

    public static void unregisterPluginOpenApiBeans(String pluginId) {
        List<Class<?>> controllerClasses = PLUGIN_CONTROLLER_CLASSES.remove(pluginId);
        if (controllerClasses == null || controllerClasses.isEmpty()) {
            return;
        }
        try {
            Field field = AbstractOpenApiResource.class.getDeclaredField("ADDITIONAL_REST_CONTROLLERS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Class<?>> additionalRestControllers =
                    (List<Class<?>>) field.get(null);
            additionalRestControllers.removeAll(controllerClasses);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(
                    "Failed to remove plugin controller classes from springdoc additional rest controllers: "
                            + pluginId, e);
        }
    }
}
