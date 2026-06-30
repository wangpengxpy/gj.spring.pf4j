/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.jpa.GJPluginJpaEntityManagerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Map;
import java.util.Set;

class JpaRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(JpaRegistrar.class);

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() { return 4; }

    @Override
    public void onBeforeContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
        String pluginId = pluginCtx.getId();
        ApplicationContext hostCtx = pluginCtx.getParent();
        Map<String, GJPluginJpaEntityManagerManager> managers =
                hostCtx.getBeansOfType(GJPluginJpaEntityManagerManager.class);
        if (managers.isEmpty()) {
            log.debug("[Plugin: {}] JPA EntityManagerManager not available " +
                    "(Hibernate not on classpath), skipping JPA initialization", pluginId);
            return;
        }
        GJPluginJpaEntityManagerManager jpaManager = managers.values().iterator().next();
        jpaManager.initializeJpaForPlugin(pluginId, pluginCtx);
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginCtx) {
        String pluginId = pluginCtx.getId();
        ApplicationContext hostCtx = pluginCtx.getParent();
        Map<String, GJPluginJpaEntityManagerManager> managers =
                hostCtx.getBeansOfType(GJPluginJpaEntityManagerManager.class);
        if (managers.isEmpty()) {
            return;
        }
        GJPluginJpaEntityManagerManager jpaManager = managers.values().iterator().next();
        jpaManager.cleanupPluginResources(pluginId, pluginCtx);
    }
}
