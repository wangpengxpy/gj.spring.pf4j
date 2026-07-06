/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

import org.springframework.context.ApplicationEvent;

/**
 * Base class for plugin authentication events.
 * Published by {@code PluginDelegatingAuthFilter} /
 * {@code PluginDelegatingAuthWebFilter} after authentication completes.
 * <p>
 * Inspired by Spring Security's {@code AuthenticationEvent} hierarchy.
 * Host applications listen via {@code @EventListener}.
 */
public abstract class PluginAuthenticationEvent extends ApplicationEvent {

    private final String pluginId;
    private final String providerName;
    private final long durationMs;

    protected PluginAuthenticationEvent(Object source, String pluginId,
                                         String providerName, long durationMs) {
        super(source);
        this.pluginId = pluginId;
        this.providerName = providerName;
        this.durationMs = durationMs;
    }

    public String getPluginId() { return pluginId; }
    public String getProviderName() { return providerName; }
    public long getDurationMs() { return durationMs; }
}
