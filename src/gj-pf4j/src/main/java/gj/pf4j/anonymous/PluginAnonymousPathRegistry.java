/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.anonymous;

import java.util.Collection;

/**
 * Registry of plugin endpoints marked with {@link AllowAnonymous @AllowAnonymous}.
 * <p>
 * Populated automatically by the framework when plugins start —
 * plugin authors only need to add the annotation. Host applications
 * inject this bean into their Spring Security configuration and call
 * {@link #isAnonymous(String, String)} on each request to decide
 * whether to permit anonymous access.
 *
 * <p><strong>Host application integration (MVC):</strong>
 * <pre>{@code
 * @Configuration
 * @EnableWebSecurity
 * @RequiredArgsConstructor
 * public class WebSecurityConfig {
 *
 *     private final PluginAnonymousPathRegistry anonymousPathRegistry;
 *
 *     @Bean
 *     public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
 *         http.authorizeHttpRequests(auth -> auth
 *             .requestMatchers(req ->
 *                 anonymousPathRegistry.isAnonymous(
 *                     req.getRequestURI(), req.getMethod())
 *             ).permitAll()
 *             // ... other rules
 *         );
 *         return http.build();
 *     }
 * }
 * }</pre>
 */
public interface PluginAnonymousPathRegistry {

    /**
     * Register an anonymous path entry. Called by the framework
     * during plugin controller registration.
     */
    void register(String pluginId, AnonymousPathEntry entry);

    /**
     * Unregister all anonymous paths belonging to the given plugin.
     */
    void unregisterByPlugin(String pluginId);

    /**
     * Check whether a request path + HTTP method combination
     * matches any registered anonymous endpoint. Called on the
     * request hot path — must be fast.
     *
     * @param requestPath the request URI (e.g. {@code /api/v3/sso/callback})
     * @param httpMethod  the HTTP method ({@code GET}, {@code POST}, etc.)
     * @return true if anonymous access is permitted
     */
    boolean isAnonymous(String requestPath, String httpMethod);

    /**
     * List all registered anonymous path entries (for auditing / monitoring).
     */
    Collection<AnonymousPathEntry> listAll();

    /**
     * List anonymous path entries for a specific plugin.
     */
    Collection<AnonymousPathEntry> listByPlugin(String pluginId);

    /**
     * Total number of registered anonymous path entries.
     */
    int getCount();
}
