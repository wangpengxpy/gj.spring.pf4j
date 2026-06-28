/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.util.Set;

/** A component that registers/unregisters plugin resources at specific lifecycle phases. */
public interface PluginResourceRegistrar {
    /** Phases this registrar participates in. */
    default Set<PluginLifecyclePhase> phases() { return Set.of(); }

    /** Execution order within a phase. Smaller = earlier. */
    default int order() { return 0; }

    default void onBeforeContextRefresh(AnnotationConfigApplicationContext ctx) {}
    default void onAfterContextRefresh(AnnotationConfigApplicationContext ctx) {}
    default void onBeforeContextClose() {}
}
