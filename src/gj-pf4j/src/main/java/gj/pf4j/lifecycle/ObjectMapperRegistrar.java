/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import gj.pf4j.GJJackson;
import gj.pf4j.GJPluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Set;

class ObjectMapperRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ObjectMapperRegistrar.class);

    private final GJPluginContext pluginContext;
    private final GenericApplicationContext mainAppCtx;

    ObjectMapperRegistrar(GJPluginContext pluginContext, GenericApplicationContext mainAppCtx) {
        this.pluginContext = pluginContext;
        this.mainAppCtx = mainAppCtx;
    }

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH);
    }

    @Override
    public int order() { return 0; }

    @Override
    public void onBeforeContextRefresh(AnnotationConfigApplicationContext ctx) {
        ObjectMapper hostMapper;
        try {
            hostMapper = GJJackson.resolveObjectMapper(this.mainAppCtx);
        } catch (Exception e) {
            log.error("[Plugin: {}] Failed to resolve ObjectMapper, using default",
                    pluginContext.getPluginId(), e);
            hostMapper = GJJackson.createDefaultObjectMapper();
        }

        ObjectMapper pluginMapper = hostMapper.copy();

        if (!ctx.containsBean("objectMapper")) {
            ctx.registerBean("objectMapper", ObjectMapper.class, () -> pluginMapper);
        }
        log.debug("[Plugin: {}] Registered isolated ObjectMapper for plugin",
                pluginContext.getPluginId());
    }
}
