/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.socketio.GJHub;
import gj.pf4j.socketio.GJHubManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Map;
import java.util.Set;

class HubRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(HubRegistrar.class);

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
        Map<String, GJHub> hubs = pluginCtx.getBeansOfType(GJHub.class);
        if (hubs.isEmpty()) {
            log.debug("[Plugin: {}] No Hub Beans found, skipping registration.", pluginId);
            return;
        }
        ApplicationContext hostCtx = pluginCtx.getParent();
        if (hostCtx.getBeansOfType(GJHubManager.class).isEmpty()) {
            log.warn("[Plugin: {}] HubManager not registered, " +
                    "unable to register {} Hub instances.", pluginId, hubs.size());
            return;
        }
        if (log.isDebugEnabled()) {
            String hubNames = hubs.values().stream()
                    .map(GJHub::getHubName)
                    .collect(java.util.stream.Collectors.joining(", "));
            log.debug("[Plugin: {}] Found {} Hubs, preparing for registration: {}",
                    pluginId, hubs.size(), hubNames);
        }
        GJHubManager hubManager = hostCtx.getBean(GJHubManager.class);
        hubManager.registerHubs(hubs.values());
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginCtx) {
        Map<String, GJHub> hubs = pluginCtx.getBeansOfType(GJHub.class);
        if (hubs.isEmpty()) {
            return;
        }
        ApplicationContext hostCtx = pluginCtx.getParent();
        if (hostCtx.getBeansOfType(GJHubManager.class).isEmpty()) {
            return;
        }
        GJHubManager hubManager = hostCtx.getBean(GJHubManager.class);
        for (GJHub hub : hubs.values()) {
            hubManager.unregisterHub(hub.getHubName());
        }
    }
}
