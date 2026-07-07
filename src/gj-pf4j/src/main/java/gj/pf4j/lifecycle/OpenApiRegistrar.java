/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.openapi.GJPluginOpenApiConfig;
import gj.pf4j.openapi.GJPluginOpenApiInfo;
import org.slf4j.Logger;
import org.springframework.context.support.GenericApplicationContext;
import org.slf4j.LoggerFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

class OpenApiRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(OpenApiRegistrar.class);

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() { return 11; }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
        String pluginId = pluginCtx.getId();
        Set<Object> controllers = scanControllers(pluginCtx);
        if (controllers.isEmpty()) {
            return;
        }
        ApplicationContext hostCtx = pluginCtx.getParent();

        GJPluginOpenApiInfo pluginOpenApiInfo = new GJPluginOpenApiInfo();
        pluginOpenApiInfo.setGroupName(pluginId);
        List<String> controllerPackages = new ArrayList<>();
        List<Class<?>> controllerClasses = new ArrayList<>();
        for (Object controller : controllers) {
            controllerPackages.add(controller.getClass().getPackageName());
            controllerClasses.add(controller.getClass());
        }
        pluginOpenApiInfo.setControllerPackages(
                controllerPackages.stream().distinct().collect(Collectors.toList()));
        pluginOpenApiInfo.setControllerClasses(controllerClasses);
        GJPluginOpenApiConfig.registerPluginOpenApiBeans(hostCtx, pluginOpenApiInfo);
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginCtx) {
        String pluginId = pluginCtx.getId();
        ApplicationContext hostCtx = pluginCtx.getParent();

        ((AbstractAutowireCapableBeanFactory) ((GenericApplicationContext) hostCtx).getBeanFactory())
                .destroySingleton(GJPluginOpenApiConfig.PLUGIN_SWAGGER_BEAN_PREFIX + pluginId);
        GJPluginOpenApiConfig.unregisterPluginOpenApiBeans(pluginId);
        Object resource = GJPluginOpenApiConfig.findMultipleOpenApiResource(hostCtx);
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

    private Set<Object> scanControllers(AnnotationConfigApplicationContext pluginCtx) {
        Set<Object> beans = new LinkedHashSet<>();
        beans.addAll(pluginCtx.getBeansWithAnnotation(Controller.class).values());
        beans.addAll(pluginCtx.getBeansWithAnnotation(RestController.class).values());
        return beans;
    }
}
