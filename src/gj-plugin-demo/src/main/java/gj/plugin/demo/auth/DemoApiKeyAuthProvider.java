package gj.plugin.demo.auth;

import gj.pf4j.security.AbstractPluginAuthenticationProvider;
import gj.pf4j.security.PluginBadCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Simple API Key authentication for the demo plugin.
 * <p>
 * Extends {@link AbstractPluginAuthenticationProvider} — the framework
 * auto-collects the plugin's URL patterns and routes matching requests
 * to this provider. No {@code supports()} coding needed.
 * <p>
 * <b>Two auth modes demonstrated:</b>
 * <ul>
 *   <li>{@code MvcUserController} — annotated {@code @PluginAuthenticated}
 *       → OR mode: host session passes through, API calls need {@code X-Demo-Api-Key}</li>
 *   <li>{@code MvcPublicController} — annotated {@code @AllowAnonymous}
 *       → completely bypasses all authentication</li>
 *   <li>Unannotated controllers (if any) — exclusive mode:
 *       provider takes over, session is ignored</li>
 * </ul>
 * <p>
 * If the request carries no API Key, {@code null} is returned and the
 * framework falls back to the host's standard authentication chain.
 */
@Component
public class DemoApiKeyAuthProvider extends AbstractPluginAuthenticationProvider {

    private static final String API_KEY_HEADER = "X-Demo-Api-Key";
    private static final String DEMO_API_KEY = "demo-secret-key";

    @Override
    public Authentication authenticate(HttpServletRequest request)
            throws PluginBadCredentialsException {
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey == null) {
            return null; // No API Key — fall back to host auth
        }
        if (!DEMO_API_KEY.equals(apiKey)) {
            throw new PluginBadCredentialsException("Invalid demo API Key");
        }
        return new UsernamePasswordAuthenticationToken(
                "demo-api-client", null, List.of());
    }
}
