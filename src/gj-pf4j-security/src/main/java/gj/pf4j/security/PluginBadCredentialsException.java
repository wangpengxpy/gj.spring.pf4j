/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

/**
 * Client-side failure: bad API key, expired token, invalid signature.
 * Logged at WARN level.
 * <p>
 * Analogy: Shiro's {@code IncorrectCredentialsException}.
 */
public class PluginBadCredentialsException extends PluginAuthenticationException {

    public PluginBadCredentialsException(String message) {
        super(message);
    }

    public PluginBadCredentialsException(String message, Throwable cause) {
        super(message, cause);
    }
}
