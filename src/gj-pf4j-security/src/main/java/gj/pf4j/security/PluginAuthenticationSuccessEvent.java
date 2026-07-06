/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

import org.springframework.security.core.Authentication;

/**
 * Published when a plugin {@code IPluginAuthenticationProvider}
 * successfully authenticates a request.
 * <p>
 * Host applications can listen via {@code @EventListener}:
 * <pre>{@code
 * @EventListener
 * public void onPluginAuthSuccess(PluginAuthenticationSuccessEvent e) {
 *     auditLog.info("Plugin [{}] auth success via {}: principal={}, {}ms",
 *         e.getPluginId(), e.getProviderName(),
 *         e.getAuthentication().getName(), e.getDurationMs());
 * }
 * }</pre>
 */
public class PluginAuthenticationSuccessEvent extends PluginAuthenticationEvent {

    private final Authentication authentication;
    private final int providerOrder;

    public PluginAuthenticationSuccessEvent(Object source, String pluginId,
                                             String providerName, long durationMs,
                                             Authentication authentication, int providerOrder) {
        super(source, pluginId, providerName, durationMs);
        this.authentication = authentication;
        this.providerOrder = providerOrder;
    }

    public Authentication getAuthentication() { return authentication; }
    public int getProviderOrder() { return providerOrder; }
}
