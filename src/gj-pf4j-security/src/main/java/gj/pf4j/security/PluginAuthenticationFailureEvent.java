/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

/**
 * Published when all plugin {@code IPluginAuthenticationProvider}s
 * fail to authenticate a request.
 * <p>
 * The {@code exception} field may be {@code null} when a provider
 * returned {@code null} instead of throwing.
 */
public class PluginAuthenticationFailureEvent extends PluginAuthenticationEvent {

    private final PluginAuthenticationException exception;
    private final int providerOrder;

    public PluginAuthenticationFailureEvent(Object source, String pluginId,
                                             String providerName, long durationMs,
                                             PluginAuthenticationException exception,
                                             int providerOrder) {
        super(source, pluginId, providerName, durationMs);
        this.exception = exception;
        this.providerOrder = providerOrder;
    }

    public PluginAuthenticationException getException() { return exception; }
    public int getProviderOrder() { return providerOrder; }
}
