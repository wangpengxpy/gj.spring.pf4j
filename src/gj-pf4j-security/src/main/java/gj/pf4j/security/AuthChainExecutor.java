/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Shared provider chain execution engine used by both
 * {@code PluginDelegatingAuthFilter} (MVC) and
 * {@code PluginDelegatingAuthWebFilter} (WebFlux).
 * <p>
 * Handles provider iteration, {@code authenticate()} invocation,
 * exception classification, logging, strategy coordination, and
 * event construction. Platform-specific concerns (SecurityContext
 * injection, error response, chain continuation) are handled by
 * each filter after receiving the result.
 */
public final class AuthChainExecutor {

    private static final Logger log = LoggerFactory.getLogger(AuthChainExecutor.class);

    private AuthChainExecutor() {}

    /**
     * Run the provider chain against the given request.
     *
     * @return the execution result — success, challenge, failure, or not-claimed
     */
    public static AuthChainResult execute(
            List<IPluginAuthenticationProvider> providers,
            HttpServletRequest request,
            AuthenticationStrategy strategy,
            String pluginId) {

        List<AuthenticationStrategy.ProviderAttempt> attempts = new ArrayList<>();
        long chainStart = System.currentTimeMillis();

        for (IPluginAuthenticationProvider provider : providers) {
            if (!provider.supports(request)) {
                continue;
            }

            long authStart = System.currentTimeMillis();
            Authentication result = null;
            PluginAuthenticationException authException = null;

            try {
                result = provider.authenticate(request);
            } catch (PluginAuthChallenge challenge) {
                authException = challenge;
            } catch (PluginBadCredentialsException e) {
                authException = e;
            } catch (PluginAuthenticationException e) {
                authException = e;
            } catch (Exception e) {
                authException = new PluginAuthServiceException(e.getMessage(), e);
            }

            long durationMs = System.currentTimeMillis() - authStart;
            String providerName = provider.getClass().getSimpleName();

            logAttempt(pluginId, providerName, result, authException, durationMs);

            AuthenticationStrategy.ProviderAttempt attempt =
                    new AuthenticationStrategy.ProviderAttempt(
                            provider, result, authException, durationMs);
            attempts.add(attempt);

            // PluginAuthChallenge — immediate response, don't continue chain
            if (authException instanceof PluginAuthChallenge challenge) {
                return AuthChainResult.challenge(challenge, attempt, pluginId,
                        providerName, durationMs, provider.getOrder());
            }

            AuthenticationStrategy.Decision decision = strategy.onAttempt(attempt);
            if (decision == AuthenticationStrategy.Decision.STOP) {
                break;
            }
        }

        // Final resolution
        try {
            Authentication finalAuth = strategy.onCompletion(attempts);
            long totalMs = System.currentTimeMillis() - chainStart;

            if (finalAuth != null && finalAuth.isAuthenticated()) {
                int ok = 0, fail = 0;
                for (var a : attempts) {
                    if (a.result() != null && a.result().isAuthenticated()) ok++;
                    else fail++;
                }
                log.info("[Plugin: {}] Auth chain: {} success, {} failed, {}ms total → principal={}",
                        pluginId, ok, fail, totalMs, finalAuth.getName());
                return AuthChainResult.success(finalAuth, attempts, pluginId, totalMs);
            }
        } catch (PluginAuthenticationException e) {
            log.warn("[Plugin: {}] Auth chain failed: {}", pluginId, e.getMessage());
            return AuthChainResult.failure(attempts, pluginId, e.getMessage());
        }

        // No success
        boolean anySupported = attempts.stream()
                .anyMatch(a -> a.exception() != null || a.result() != null);
        long totalMs = System.currentTimeMillis() - chainStart;

        if (anySupported) {
            String providerNames = providers.stream()
                    .map(p -> p.getClass().getSimpleName())
                    .collect(Collectors.joining(", "));
            log.warn("[Plugin: {}] Auth chain exhausted: {} provider(s) all failed in {}ms",
                    pluginId, providerNames, totalMs);
            return AuthChainResult.failure(attempts, pluginId, null);
        }

        log.debug("[Plugin: {}] No provider claimed — falling back to host auth", pluginId);
        return AuthChainResult.notClaimed();
    }

    // ──── Logging ─────────────────────────────────────────────────

    private static void logAttempt(String pluginId, String providerName,
                                    Authentication result,
                                    PluginAuthenticationException authException,
                                    long durationMs) {
        if (authException instanceof PluginAuthChallenge challenge) {
            log.debug("[Plugin: {}] Auth CHALLENGE via {} in {}ms: status={}",
                    pluginId, providerName, durationMs, challenge.getStatusCode());
        } else if (authException instanceof PluginBadCredentialsException) {
            log.warn("[Plugin: {}] Bad credentials via {} in {}ms: {}",
                    pluginId, providerName, durationMs, authException.getMessage());
        } else if (authException instanceof PluginAuthenticationException) {
            log.error("[Plugin: {}] Auth FAILURE via {} in {}ms: {}",
                    pluginId, providerName, durationMs, authException.getMessage());
        } else if (authException != null) {
            log.error("[Plugin: {}] Auth UNEXPECTED via {} in {}ms: {}",
                    pluginId, providerName, durationMs,
                    authException.getMessage(), authException);
        } else if (result == null) {
            log.warn("[Plugin: {}] Auth NULL via {} in {}ms — returned null",
                    pluginId, providerName, durationMs);
        } else {
            log.info("[Plugin: {}] Auth SUCCESS via {} in {}ms: principal={}",
                    pluginId, providerName, durationMs, result.getName());
        }
    }

    // ──── Result type ─────────────────────────────────────────────

    public interface AuthChainResult {

        static AuthChainResult success(Authentication auth,
                                        List<AuthenticationStrategy.ProviderAttempt> attempts,
                                        String pluginId, long durationMs) {
            return new Success(auth, attempts, pluginId, durationMs);
        }

        static AuthChainResult challenge(PluginAuthChallenge exception,
                                          AuthenticationStrategy.ProviderAttempt attempt,
                                          String pluginId, String providerName,
                                          long durationMs, int order) {
            return new Challenge(exception, attempt, pluginId, providerName, durationMs, order);
        }

        static AuthChainResult failure(List<AuthenticationStrategy.ProviderAttempt> attempts,
                                        String pluginId, String message) {
            return new Failure(attempts, pluginId, message);
        }

        static AuthChainResult notClaimed() {
            return NOT_CLAIMED;
        }

        AuthChainResult NOT_CLAIMED = new NotClaimed();

        record Success(
                Authentication authentication,
                List<AuthenticationStrategy.ProviderAttempt> attempts,
                String pluginId, long durationMs
        ) implements AuthChainResult {}

        record Challenge(
                PluginAuthChallenge exception,
                AuthenticationStrategy.ProviderAttempt attempt,
                String pluginId, String providerName,
                long durationMs, int order
        ) implements AuthChainResult {}

        record Failure(
                List<AuthenticationStrategy.ProviderAttempt> attempts,
                String pluginId, String message
        ) implements AuthChainResult {}

        record NotClaimed() implements AuthChainResult {}
    }
}
