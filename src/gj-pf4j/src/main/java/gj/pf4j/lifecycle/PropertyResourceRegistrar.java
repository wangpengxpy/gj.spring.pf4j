/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePropertySource;

import java.util.Set;

class PropertyResourceRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(PropertyResourceRegistrar.class);

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH);
    }

    @Override
    public int order() { return 1; }

    @Override
    public void onBeforeContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
        String pluginId = pluginCtx.getId();
        try {
            String resourceName = pluginId + ".properties";
            Resource resource = new ClassPathResource(resourceName, pluginCtx.getClassLoader());
            if (resource.exists()) {
                pluginCtx.getEnvironment()
                        .getPropertySources()
                        .addFirst(new ResourcePropertySource(resource));
            } else {
                log.warn("Resource '{}' does not exist in classpath", resourceName);
            }
        } catch (Exception e) {
            log.error("Failed to load {}.properties", pluginId, e);
            throw new RuntimeException(String.format("Failed to load %s.properties", pluginId), e);
        }
    }
}
