/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.GJPluginContext;
import gj.pf4j.socketio.GJHub;
import gj.pf4j.socketio.GJHubManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Map;
import java.util.Set;

class HubRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(HubRegistrar.class);

    private final GJPluginContext pluginContext;
    private final GenericApplicationContext mainAppCtx;

    HubRegistrar(GJPluginContext pluginContext, GenericApplicationContext mainAppCtx) {
        this.pluginContext = pluginContext;
        this.mainAppCtx = mainAppCtx;
    }

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() { return 11; }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext ctx) {
        String pluginId = pluginContext.getPluginId();
        Map<String, GJHub> hubs =
                pluginContext.getApplicationContext().getBeansOfType(GJHub.class);
        if (hubs.isEmpty()) {
            log.debug("[Plugin: {}] No Hub Beans found, skipping registration.", pluginId);
            return;
        }
        if (mainAppCtx.getBeansOfType(GJHubManager.class).isEmpty()) {
            log.warn("[Plugin: {}] HubManager not registered, " +
                    "unable to register {} Hub instances.", pluginId, hubs.size());
            return;
        }
        if (log.isDebugEnabled()) {
            String hubNames = String.join(", ", hubs.keySet());
            log.debug("[Plugin: {}] Found {} Hubs, preparing for registration: {}",
                    pluginId, hubs.size(), hubNames);
        }
        GJHubManager hubManager = mainAppCtx.getBean(GJHubManager.class);
        hubManager.registerHubs(hubs.values());
    }

    @Override
    public void onBeforeContextClose() {
        Map<String, GJHub> hubs =
                pluginContext.getApplicationContext().getBeansOfType(GJHub.class);
        if (hubs.isEmpty()) {
            return;
        }
        if (mainAppCtx.getBeansOfType(GJHubManager.class).isEmpty()) {
            return;
        }
        GJHubManager hubManager = mainAppCtx.getBean(GJHubManager.class);
        for (String hubName : hubs.keySet()) {
            hubManager.unregisterHub(hubName);
        }
    }
}
