/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.migration.GJPluginModelMigrator;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Set;

class MigrationRegistrar implements PluginResourceRegistrar {

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH);
    }

    @Override
    public int order() { return 5; }

    @Override
    public void onBeforeContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
        if (pluginCtx.getParent().getBeansOfType(GJPluginModelMigrator.class).isEmpty()) {
            return;
        }
        GJPluginModelMigrator migrator = pluginCtx.getParent().getBean(GJPluginModelMigrator.class);
        migrator.migrate(pluginCtx.getId(), pluginCtx.getClassLoader());
    }
}
