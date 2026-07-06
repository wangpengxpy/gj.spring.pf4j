/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.core;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that a controller method or class should use plugin custom authentication.
 * <p>
 * When present on a method or class, the framework switches to
 * <strong>OR authentication mode</strong> for those endpoints:
 * existing session authentication is tried first; if no valid session exists,
 * the plugin's {@code IPluginAuthenticationProvider} is invoked.
 * <p>
 * Without this annotation, plugins operate in <strong>exclusive authentication
 * mode</strong> — session authentication is ignored and every request must pass
 * through the plugin's {@code authenticate()} method.
 * <p>
 * <strong>Usage:</strong>
 * <pre>{@code
 * @PluginAuthenticated
 * @PostMapping("/payment/refund")
 * public Result refund() { ... }   // session first, then plugin auth
 *
 * @GetMapping("/payment/order/{id}")
 * public Result getOrder() { ... } // host session (not annotated)
 * }</pre>
 *
 * <p><strong>Interaction with {@link AllowAnonymous}:</strong>
 * {@code @AllowAnonymous} takes precedence. If both annotations are present
 * on the same method, the endpoint is treated as anonymous.
 *
 * <p>Effective in both MVC and WebFlux environments.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PluginAuthenticated {
}
