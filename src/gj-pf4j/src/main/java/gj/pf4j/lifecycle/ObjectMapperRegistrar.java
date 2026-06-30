/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import gj.pf4j.GJJackson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Set;

class ObjectMapperRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ObjectMapperRegistrar.class);

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH);
    }

    @Override
    public int order() { return 0; }

    @Override
    public void onBeforeContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
        String pluginId = pluginCtx.getId();
        ObjectMapper hostMapper;
        try {
            hostMapper = GJJackson.resolveObjectMapper(pluginCtx.getParent());
        } catch (Exception e) {
            log.error("[Plugin: {}] Failed to resolve ObjectMapper, using default", pluginId, e);
            hostMapper = GJJackson.createDefaultObjectMapper();
        }

        ObjectMapper pluginMapper = hostMapper.copy();

        if (!pluginCtx.containsBean("objectMapper")) {
            pluginCtx.registerBean("objectMapper", ObjectMapper.class, () -> pluginMapper);
        }
        log.debug("[Plugin: {}] Registered isolated ObjectMapper for plugin", pluginId);
    }
}
