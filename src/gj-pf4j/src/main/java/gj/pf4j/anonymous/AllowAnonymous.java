/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.anonymous;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Mark a plugin controller class or handler method for anonymous access.
 * <p>
 * When placed on a controller class, all request mappings in that class
 * are treated as anonymous. When placed on a method, only that specific
 * mapping is anonymous. Method-level annotation takes precedence over
 * class-level.
 * <p>
 * Matching is scoped to HTTP method + URL pattern, so
 * {@code POST /api/sso/{id}} can be anonymous while
 * {@code GET  /api/sso/{id}} remains authenticated.
 *
 * <p><strong>Usage:</strong>
 * <pre>{@code
 * @AllowAnonymous(reason = "SSO callback endpoint invoked by third-party identity provider")
 * @PostMapping("/sso/callback")
 * public Result handleCallback(@RequestBody SsoCallbackRequest req) { ... }
 * }</pre>
 *
 * <p><strong>Host application integration:</strong> inject
 * {@link PluginAnonymousPathRegistry} and call
 * {@code registry.isAnonymous(requestPath, httpMethod)} from your
 * Spring Security configuration.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowAnonymous {

    /**
     * Optional reason explaining why this endpoint is open to anonymous access.
     * Used for operational auditing.
     */
    String reason() default "";
}
