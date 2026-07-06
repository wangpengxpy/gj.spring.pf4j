/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

import org.springframework.security.core.Authentication;
import java.util.List;

/**
 * Authentication chain strategy — controls how multiple
 * {@link IPluginAuthenticationProvider}s are orchestrated.
 * <p>
 * Inspired by Apache Shiro's {@code AuthenticationStrategy}.
 * Plug a custom strategy into {@code PluginDelegatingAuthFilter}
 * by overriding the {@code authenticationStrategy} bean.
 *
 * <p>The default strategy is {@link AtLeastOneSuccessfulStrategy}.
 */
public interface AuthenticationStrategy {

    /**
     * Callback after a single provider attempt completes.
     *
     * @return {@link Decision#CONTINUE} to try the next provider,
     *         {@link Decision#STOP} to terminate the chain immediately
     */
    Decision onAttempt(ProviderAttempt attempt);

    /**
     * Final resolution after all providers have been attempted.
     *
     * @return the resolved {@code Authentication}, or {@code null}
     *         if no provider produced a valid result
     * @throws PluginAuthenticationException when the chain should fail
     *         (e.g. {@code PluginAuthChallenge})
     */
    Authentication onCompletion(List<ProviderAttempt> attempts)
            throws PluginAuthenticationException;

    enum Decision { CONTINUE, STOP }

    record ProviderAttempt(
            IPluginAuthenticationProvider provider,
            Authentication result,
            PluginAuthenticationException exception,
            long durationMs
    ) {}
}
