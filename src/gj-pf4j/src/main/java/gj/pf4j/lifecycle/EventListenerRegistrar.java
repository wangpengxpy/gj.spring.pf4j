/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.eventbus.GJPluginLocalEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Set;

class EventListenerRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(EventListenerRegistrar.class);

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() { return 13; }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
        ApplicationContext hostCtx = pluginCtx.getParent();
        if (hostCtx.getBeansOfType(GJPluginLocalEventBus.class).isEmpty()) {
            log.debug("[Plugin: {}] GJPluginLocalEventBus not registered, " +
                    "skipping listener registration", pluginCtx.getId());
            return;
        }
        GJPluginLocalEventBus eventBus = hostCtx.getBean(GJPluginLocalEventBus.class);
        eventBus.registerListeners(pluginCtx.getId(), pluginCtx);
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginCtx) {
        ApplicationContext hostCtx = pluginCtx.getParent();
        if (hostCtx.getBeansOfType(GJPluginLocalEventBus.class).isEmpty()) {
            return;
        }
        GJPluginLocalEventBus eventBus = hostCtx.getBean(GJPluginLocalEventBus.class);
        eventBus.unregisterListeners(pluginCtx.getId());
    }
}
