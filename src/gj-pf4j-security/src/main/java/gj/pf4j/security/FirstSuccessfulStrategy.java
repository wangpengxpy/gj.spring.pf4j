/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

import org.springframework.security.core.Authentication;
import java.util.List;

/**
 * Fast-fail authentication strategy — the first provider that returns
 * a non-null, authenticated result wins. No further providers are tried.
 * <p>
 * Inspired by Apache Shiro's {@code FirstSuccessfulStrategy}.
 */
public class FirstSuccessfulStrategy implements AuthenticationStrategy {

    @Override
    public Decision onAttempt(ProviderAttempt attempt) {
        if (attempt.exception() instanceof PluginAuthChallenge) {
            return Decision.STOP;
        }
        if (attempt.result() != null && attempt.result().isAuthenticated()) {
            return Decision.STOP;
        }
        return Decision.CONTINUE;
    }

    @Override
    public Authentication onCompletion(List<ProviderAttempt> attempts) {
        for (ProviderAttempt attempt : attempts) {
            if (attempt.result() != null && attempt.result().isAuthenticated()) {
                return attempt.result();
            }
        }
        for (int i = attempts.size() - 1; i >= 0; i--) {
            if (attempts.get(i).exception() != null) {
                throw attempts.get(i).exception();
            }
        }
        if (!attempts.isEmpty()) {
            throw new PluginAuthenticationException(
                    "All " + attempts.size() + " provider(s) failed to authenticate");
        }
        return null;
    }
}
