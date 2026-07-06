/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

import org.springframework.security.core.AuthenticationException;

/**
 * Base exception for plugin authentication failures.
 * Extends Spring Security's {@code AuthenticationException} so
 * {@code ExceptionTranslationFilter} handles it correctly.
 */
public class PluginAuthenticationException extends AuthenticationException {

    public PluginAuthenticationException(String message) {
        super(message);
    }

    public PluginAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
