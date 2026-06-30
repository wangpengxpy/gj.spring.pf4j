/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.mybatis.GJPluginMybatisSqlSessionManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Set;

class MybatisRegistrar implements PluginResourceRegistrar {

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() { return 3; }

    @Override
    public void onBeforeContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
        ApplicationContext hostCtx = pluginCtx.getParent();
        GJPluginMybatisSqlSessionManager mybatisRegistry =
                hostCtx.getBean(GJPluginMybatisSqlSessionManager.class);
        mybatisRegistry.initializeMyBatisForPlugin(pluginCtx.getId(), pluginCtx);
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginCtx) {
        GJPluginMybatisSqlSessionManager mybatisRegistry =
                pluginCtx.getBean(GJPluginMybatisSqlSessionManager.class);
        mybatisRegistry.cleanupPluginResources(pluginCtx.getId());
    }
}
