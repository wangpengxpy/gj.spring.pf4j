/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.GJPluginContext;
import gj.pf4j.quartzjob.PluginJobManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Set;

class QuartzJobRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(QuartzJobRegistrar.class);

    private final GJPluginContext pluginContext;
    private final GenericApplicationContext mainAppCtx;

    QuartzJobRegistrar(GJPluginContext pluginContext, GenericApplicationContext mainAppCtx) {
        this.pluginContext = pluginContext;
        this.mainAppCtx = mainAppCtx;
    }

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() { return 14; }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext ctx) {
        if (mainAppCtx.getBeansOfType(PluginJobManager.class).isEmpty()) {
            log.debug("[Plugin: {}] PluginJobManager not registered, skipping job registration",
                    pluginContext.getPluginId());
            return;
        }
        PluginJobManager jobManager = mainAppCtx.getBean(PluginJobManager.class);
        jobManager.registerJobs(pluginContext.getPluginId(), pluginContext.getApplicationContext());
    }

    @Override
    public void onBeforeContextClose() {
        if (mainAppCtx.getBeansOfType(PluginJobManager.class).isEmpty()) {
            return;
        }
        PluginJobManager jobManager = mainAppCtx.getBean(PluginJobManager.class);
        jobManager.unregisterJobs(pluginContext.getPluginId());
    }
}
