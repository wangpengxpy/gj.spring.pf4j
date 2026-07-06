/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

/**
 * Plugin HTTP request utility methods.
 * Encapsulates contextPath handling so every path-matching component
 * consistently receives the application-relative path.
 */
public final class PluginHttpUtils {

    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    static {
        PATH_HELPER.setAlwaysUseFullPath(false);
    }

    private PluginHttpUtils() {}

    /**
     * Extract the request path without the context path.
     * Equivalent to {@code request.getRequestURI() - request.getContextPath()}.
     *
     * <p>Must be used instead of bare {@code request.getRequestURI()} for all
     * path-matching operations — HandlerMapping patterns are context-path-free,
     * and a mismatch will cause silent failures when contextPath is not empty.
     *
     * <p>Works correctly under WebFlux via {@code WebFluxHttpServletRequestAdapter}.
     */
    public static String getPathWithinApplication(HttpServletRequest request) {
        return PATH_HELPER.getPathWithinApplication(request);
    }
}
