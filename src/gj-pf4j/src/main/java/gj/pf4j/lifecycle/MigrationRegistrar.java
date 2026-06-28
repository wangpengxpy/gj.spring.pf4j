/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.GJPluginContext;
import gj.pf4j.migration.GJPluginModelMigrator;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Set;

class MigrationRegistrar implements PluginResourceRegistrar {

    private final GJPluginContext pluginContext;
    private final GenericApplicationContext mainAppCtx;

    MigrationRegistrar(GJPluginContext pluginContext, GenericApplicationContext mainAppCtx) {
        this.pluginContext = pluginContext;
        this.mainAppCtx = mainAppCtx;
    }

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH);
    }

    @Override
    public int order() { return 5; }

    @Override
    public void onBeforeContextRefresh(AnnotationConfigApplicationContext ctx) {
        if (this.mainAppCtx.getBeansOfType(GJPluginModelMigrator.class).isEmpty()) {
            return;
        }
        GJPluginModelMigrator migrator = this.mainAppCtx.getBean(GJPluginModelMigrator.class);
        migrator.migrate(pluginContext.getPluginId(), pluginContext.getClassLoader());
    }
}
