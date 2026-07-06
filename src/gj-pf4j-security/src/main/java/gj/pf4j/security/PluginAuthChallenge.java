/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Not an error — a normal authentication control-flow signal.
 * <p>
 * Thrown by {@code authenticate()} when the caller must complete an
 * out-of-band challenge before authentication can proceed. The filter
 * responds by setting the given status code + headers rather than 401.
 * <p>
 * {@link #fillInStackTrace()} is overridden to skip stack trace
 * generation — this is a control-flow signal, not a crash.
 * <p>
 * Common uses:
 * <ul>
 *   <li>OAuth2 / SAML redirect: {@code new PluginAuthChallenge(redirectUrl)}</li>
 *   <li>Bearer challenge: {@code new PluginAuthChallenge(401,
 *       Map.of("WWW-Authenticate", "Bearer realm=\"api\""))}</li>
 *   <li>Arbitrary challenge: any statusCode + headers combination</li>
 * </ul>
 */
public class PluginAuthChallenge extends PluginAuthenticationException {

    private final int statusCode;
    private final Map<String, String> headers;

    /** OAuth2 / SAML redirect shortcut (302 + Location). */
    public PluginAuthChallenge(String redirectUrl) {
        super("Authentication challenge — redirect to: " + redirectUrl);
        this.statusCode = HttpServletResponse.SC_MOVED_TEMPORARILY;
        this.headers = Map.of("Location", redirectUrl);
    }

    /** Generic challenge with custom status code and headers. */
    public PluginAuthChallenge(int statusCode, Map<String, String> headers) {
        super("Authentication challenge — status " + statusCode);
        this.statusCode = statusCode;
        this.headers = Map.copyOf(headers);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this; // No stack trace — control flow, not crash
    }

    public int getStatusCode() { return statusCode; }
    public Map<String, String> getHeaders() { return headers; }
}
