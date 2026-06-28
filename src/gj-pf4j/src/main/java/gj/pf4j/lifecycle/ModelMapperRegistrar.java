/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.GJPluginContext;
import gj.pf4j.modelmapper.GJPluginModelMapperRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Set;

class ModelMapperRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ModelMapperRegistrar.class);

    private final GJPluginContext pluginContext;

    ModelMapperRegistrar(GJPluginContext pluginContext) {
        this.pluginContext = pluginContext;
    }

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH);
    }

    @Override
    public int order() { return 12; }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext ctx) {
        GJPluginModelMapperRegistry modelMapperRegistry =
                ctx.getBean(GJPluginModelMapperRegistry.class);
        try {
            modelMapperRegistry.registerModelMappers(
                    pluginContext.getPluginId(), pluginContext.getApplicationContext());
        } catch (Exception ignored) {
            log.debug("[Plugin: {}] ModelMapper registration skipped or failed: {}",
                    pluginContext.getPluginId(), ignored.getMessage());
        }
    }
}
