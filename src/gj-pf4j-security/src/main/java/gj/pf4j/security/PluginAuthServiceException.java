/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

/**
 * Server-side failure: external auth service unavailable.
 * Logged at ERROR level.
 * <p>
 * Analogy: Shiro's {@code AuthenticationException} (system-side).
 */
public class PluginAuthServiceException extends PluginAuthenticationException {

    public PluginAuthServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
