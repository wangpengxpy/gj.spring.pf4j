/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

/**
 * Plugin authentication provider SPI.
 * <p>
 * Declared as a Spring {@code @Component} in the plugin's application context.
 * Discovered automatically by {@code AuthRegistrar} at plugin startup.
 * <p>
 * Routing semantics (inspired by Shiro {@code Realm.supports()}):
 * <ul>
 *   <li>{@code supports()} returns {@code true}  → framework delegates auth to this provider.
 *       On failure, the next provider (if any) is tried. Returns 401 only if all providers fail.</li>
 *   <li>{@code supports()} returns {@code false} → falls back to host standard auth.
 *       If no provider claims the request, host auth is used.</li>
 *   <li>No provider bean registered              → all requests use host standard auth</li>
 * </ul>
 */
public interface IPluginAuthenticationProvider {

    /**
     * Whether this provider handles the given request.
     * Default {@code true} — all requests to this plugin are authenticated here.
     * Override for fine-grained routing (e.g. check headers, parameters).
     *
     * <p>Must be fast — called on every request. No IO.
     *
     * @return true to delegate auth to this provider; false to fall back to host auth
     */
    default boolean supports(HttpServletRequest request) {
        return true;
    }

    /**
     * Perform authentication.
     *
     * @return non-null {@link Authentication} on success, set into {@code SecurityContext}
     * @throws PluginAuthenticationException on failure — framework returns 401
     */
    Authentication authenticate(HttpServletRequest request)
            throws PluginAuthenticationException;

    /**
     * Priority. Lower values are tried first by the filter when multiple
     * providers are registered for the same plugin (Shiro chain pattern).
     */
    default int getOrder() {
        return 500;
    }
}
