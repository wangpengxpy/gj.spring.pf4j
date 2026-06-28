/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.anonymous;

import java.util.Collection;

/**
 * Read-only registry of plugin anonymous endpoints.
 * Injected by the host application into its Spring Security configuration
 * to query anonymous paths and for operational visibility.
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
