/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

import gj.pf4j.utils.PluginHttpUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.util.AntPathMatcher;

import java.util.Map;
import java.util.Set;

/**
 * Base class for simple-layer plugin authentication.
 * <p>
 * Plugins extend this class instead of implementing
 * {@link IPluginAuthenticationProvider} directly —
 * the framework handles {@link #supports(HttpServletRequest)} routing
 * automatically based on the plugin's registered URL patterns.
 * <p>
 * Each plugin may declare at most one instance of this class.
 * Multiple instances result in a WARN log and only the first is kept.
 * <p>
 * Works identically in MVC and WebFlux environments.
 */
public abstract class AbstractPluginAuthenticationProvider
        implements IPluginAuthenticationProvider {

    private Map<String, Set<String>> pluginPaths = Map.of();
    private final AntPathMatcher matcher = new AntPathMatcher();

    /**
     * Framework-internal. Injects the URL patterns this provider should
     * handle, grouped by HTTP method. Called by {@code AuthRegistrar}.
     */
    void setPluginPaths(Map<String, Set<String>> paths) {
        this.pluginPaths = (paths != null) ? Map.copyOf(paths) : Map.of();
        this.matcher.setCaseSensitive(false);
    }

    @Override
    public boolean supports(HttpServletRequest request) {
        Set<String> paths = pluginPaths.get(request.getMethod());
        if (paths == null || paths.isEmpty()) {
            return false;
        }
        String uri = PluginHttpUtils.getPathWithinApplication(request);
        return paths.stream().anyMatch(p -> matcher.match(p, uri));
    }

    @Override
    public int getOrder() {
        return 500;
    }

    @Override
    public abstract Authentication authenticate(HttpServletRequest request)
            throws PluginAuthenticationException;
}
