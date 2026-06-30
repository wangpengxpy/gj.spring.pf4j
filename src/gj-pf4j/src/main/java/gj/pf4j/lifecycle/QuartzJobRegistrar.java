/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.quartzjob.PluginJobManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Set;

class QuartzJobRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(QuartzJobRegistrar.class);

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() { return 14; }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
        ApplicationContext hostCtx = pluginCtx.getParent();
        if (hostCtx.getBeansOfType(PluginJobManager.class).isEmpty()) {
            log.debug("[Plugin: {}] PluginJobManager not registered, skipping job registration",
                    pluginCtx.getId());
            return;
        }
        PluginJobManager jobManager = hostCtx.getBean(PluginJobManager.class);
        jobManager.registerJobs(pluginCtx.getId(), pluginCtx);
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginCtx) {
        ApplicationContext hostCtx = pluginCtx.getParent();
        if (hostCtx.getBeansOfType(PluginJobManager.class).isEmpty()) {
            return;
        }
        PluginJobManager jobManager = hostCtx.getBean(PluginJobManager.class);
        jobManager.unregisterJobs(pluginCtx.getId());
    }
}
