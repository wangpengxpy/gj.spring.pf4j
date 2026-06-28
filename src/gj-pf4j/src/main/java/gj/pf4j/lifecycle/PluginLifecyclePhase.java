/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

/** Lifecycle phases of a plugin's Spring context. Each phase corresponds to a specific state transition. */
public enum PluginLifecyclePhase {
    /** Before context.refresh() — register infrastructure beans (MyBatis, JPA, i18n, etc.) */
    BEFORE_CONTEXT_REFRESH,
    /** After context.refresh() + user hooks — register externally visible services (Controller, Hub, Job, etc.) */
    AFTER_CONTEXT_REFRESH,
    /** Before context.close() — unregister services from the host application */
    BEFORE_CONTEXT_CLOSE
}
