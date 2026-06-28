/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.GJPluginContext;
import gj.pf4j.eventbus.GJPluginLocalEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Set;

class EventListenerRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(EventListenerRegistrar.class);

    private final GJPluginContext pluginContext;
    private final GenericApplicationContext mainAppCtx;

    EventListenerRegistrar(GJPluginContext pluginContext, GenericApplicationContext mainAppCtx) {
        this.pluginContext = pluginContext;
        this.mainAppCtx = mainAppCtx;
    }

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() { return 13; }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext ctx) {
        if (mainAppCtx.getBeansOfType(GJPluginLocalEventBus.class).isEmpty()) {
            log.debug("[Plugin: {}] GJPluginLocalEventBus not registered, " +
                    "skipping listener registration", pluginContext.getPluginId());
            return;
        }
        GJPluginLocalEventBus eventBus = mainAppCtx.getBean(GJPluginLocalEventBus.class);
        eventBus.registerListeners(pluginContext.getPluginId(), pluginContext.getApplicationContext());
    }

    @Override
    public void onBeforeContextClose() {
        if (mainAppCtx.getBeansOfType(GJPluginLocalEventBus.class).isEmpty()) {
            return;
        }
        GJPluginLocalEventBus eventBus = mainAppCtx.getBean(GJPluginLocalEventBus.class);
        eventBus.unregisterListeners(pluginContext.getPluginId());
    }
}
