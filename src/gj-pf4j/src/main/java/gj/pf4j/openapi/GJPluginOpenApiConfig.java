/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.openapi;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

public class GJPluginOpenApiConfig {
    public static final String PLUGIN_SWAGGER_BEAN_PREFIX = "pluginGroupedOpenApi-";

    public static void registerPluginOpenApiBeans(ApplicationContext applicationContext, GJPluginOpenApiInfo pluginSwaggerInfo) {
        String groupName = pluginSwaggerInfo.getGroupName();
        groupName = groupName.trim().toLowerCase();
        if (groupName.trim().isEmpty()) {
            return;
        }
        String beanName = PLUGIN_SWAGGER_BEAN_PREFIX + groupName;
        String finalGroupName = groupName;
        GroupedOpenApi groupedOpenApi = GroupedOpenApi.builder()
                .group(finalGroupName.trim())
                .displayName(finalGroupName.trim())
                .packagesToScan(pluginSwaggerInfo.getControllerPackages().toArray(new String[0]))
                .build();
        ((GenericApplicationContext) applicationContext).getBeanFactory().registerSingleton(beanName, groupedOpenApi);
    }
}
