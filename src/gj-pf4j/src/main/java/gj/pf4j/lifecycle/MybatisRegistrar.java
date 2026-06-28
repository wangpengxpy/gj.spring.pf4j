/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.GJPluginContext;
import gj.pf4j.mybatis.GJPluginMybatisSqlSessionManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Set;

class MybatisRegistrar implements PluginResourceRegistrar {

    private final GJPluginContext pluginContext;
    private final GenericApplicationContext mainAppCtx;

    MybatisRegistrar(GJPluginContext pluginContext, GenericApplicationContext mainAppCtx) {
        this.pluginContext = pluginContext;
        this.mainAppCtx = mainAppCtx;
    }

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() { return 3; }

    @Override
    public void onBeforeContextRefresh(AnnotationConfigApplicationContext ctx) {
        String pluginId = pluginContext.getPluginId();
        GJPluginMybatisSqlSessionManager mybatisRegistry =
                this.mainAppCtx.getBean(GJPluginMybatisSqlSessionManager.class);
        mybatisRegistry.initializeMyBatisForPlugin(pluginId, ctx);
    }

    @Override
    public void onBeforeContextClose() {
        String pluginId = pluginContext.getPluginId();
        AnnotationConfigApplicationContext ctx = (AnnotationConfigApplicationContext)
                pluginContext.getApplicationContext();
        GJPluginMybatisSqlSessionManager mybatisRegistry =
                ctx.getBean(GJPluginMybatisSqlSessionManager.class);
        mybatisRegistry.cleanupPluginResources(pluginId);
    }
}
