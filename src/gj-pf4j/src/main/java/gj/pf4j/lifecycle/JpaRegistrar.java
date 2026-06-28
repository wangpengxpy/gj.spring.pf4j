/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.GJPluginContext;
import gj.pf4j.jpa.GJPluginJpaEntityManagerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Set;

class JpaRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(JpaRegistrar.class);

    private final GJPluginContext pluginContext;
    private final GenericApplicationContext mainAppCtx;

    JpaRegistrar(GJPluginContext pluginContext, GenericApplicationContext mainAppCtx) {
        this.pluginContext = pluginContext;
        this.mainAppCtx = mainAppCtx;
    }

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() { return 4; }

    @Override
    public void onBeforeContextRefresh(AnnotationConfigApplicationContext ctx) {
        String pluginId = pluginContext.getPluginId();
        var managers = this.mainAppCtx.getBeansOfType(GJPluginJpaEntityManagerManager.class);
        if (managers.isEmpty()) {
            log.debug("[Plugin: {}] JPA EntityManagerManager not available " +
                    "(Hibernate not on classpath), skipping JPA initialization", pluginId);
            return;
        }
        GJPluginJpaEntityManagerManager jpaManager = managers.values().iterator().next();
        jpaManager.initializeJpaForPlugin(pluginId, ctx);
    }

    @Override
    public void onBeforeContextClose() {
        String pluginId = pluginContext.getPluginId();
        var managers = this.mainAppCtx.getBeansOfType(GJPluginJpaEntityManagerManager.class);
        if (managers.isEmpty()) {
            return;
        }
        GJPluginJpaEntityManagerManager jpaManager = managers.values().iterator().next();
        jpaManager.cleanupPluginResources(pluginId, pluginContext.getApplicationContext());
    }
}
