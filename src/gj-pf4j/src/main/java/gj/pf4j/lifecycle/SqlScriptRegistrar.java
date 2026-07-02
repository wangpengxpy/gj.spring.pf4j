/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.migration.script.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Set;

/**
 * Executes SQL scripts from plugin classpath {@code scripts/{dbType}/NN-description.sql}
 * during {@link PluginLifecyclePhase#BEFORE_CONTEXT_REFRESH}.
 * <p>
 * Runs before {@link MigrationRegistrar} so raw DDL scripts create tables first,
 * then entity-based migration only adds missing columns.
 */
class SqlScriptRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(SqlScriptRegistrar.class);

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH);
    }

    @Override
    public int order() { return 5; }

    @Override
    public void onBeforeContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
        ApplicationContext hostCtx = pluginCtx.getParent();
        if (hostCtx.getBeansOfType(ScriptRunner.class).isEmpty()) {
            log.debug("[Plugin: {}] ScriptRunner not registered, skipping SQL scripts",
                    pluginCtx.getId());
            return;
        }
        ScriptRunner runner = hostCtx.getBean(ScriptRunner.class);
        String basePackage = pluginCtx.getId().replace('-', '.');
        runner.runFromPlugin(basePackage, pluginCtx.getClassLoader());
    }
}
