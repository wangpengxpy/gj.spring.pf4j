/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.util.Set;

/**
 * Plugin resource registrar — registers/unregisters plugin resources at specific lifecycle phases.
 * <p>
 * All methods are default; implement only the phases you need.
 * External registrars are Spring singleton beans — they must be stateless,
 * with all per-plugin context obtained from the method parameter.
 * <p>
 * The host ApplicationContext is available via {@code pluginCtx.getParent()}.
 * The plugin ID is available via {@code pluginCtx.getId()}.
 */
public interface PluginResourceRegistrar {

    /** Lifecycle phases this registrar participates in. Default is empty (no-op). */
    default Set<PluginLifecyclePhase> phases() {
        return Set.of();
    }

    /**
     * Execution order within a phase. Smaller values execute earlier.
     * Built-in registrars occupy [0, 99]. External registrars default to 100
     * (after all built-ins). Override to a value in 1-99 to insert between built-ins.
     */
    default int order() {
        return 100;
    }

    /**
     * Called before the plugin Spring context is refreshed.
     * The context is not yet refreshed — register beans or add PropertySources here.
     *
     * @param pluginCtx the plugin's ApplicationContext (not yet refreshed)
     */
    default void onBeforeContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
    }

    /**
     * Called after the plugin Spring context is refreshed and
     * {@code afterApplicationContextReady} hooks have executed.
     * All plugin beans are initialized — scan and register with host managers here.
     *
     * @param pluginCtx the plugin's ApplicationContext (fully refreshed)
     */
    default void onAfterContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
    }

    /**
     * Called before the plugin Spring context is closed.
     * Perform reverse cleanup: unregister from host managers, close connections,
     * flush buffers. The plugin context is still alive — beans are queryable.
     *
     * @param pluginCtx the plugin's ApplicationContext (still alive, about to close)
     */
    default void onBeforeContextClose(AnnotationConfigApplicationContext pluginCtx) {
    }
}
