/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security.servlet;

import gj.pf4j.GJPluginFilterPosition;
import gj.pf4j.GJPluginFilterRegistry;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Composite servlet filter — pre-registered in the host's
 * {@code SecurityFilterChain} at a specific position. Delegates to
 * dynamically-registered plugin filters (sandboxed — one plugin's
 * failure does not affect others).
 */
public class PluginCompositeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PluginCompositeFilter.class);

    private final GJPluginFilterRegistry registry;
    private final GJPluginFilterPosition position;

    public PluginCompositeFilter(GJPluginFilterRegistry registry, GJPluginFilterPosition position) {
        this.registry = registry;
        this.position = position;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain)
            throws ServletException, IOException {
        List<Filter> filters = registry.getFilters(position);
        if (filters.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        // Build a temporary chain (reverse iteration for correct order)
        FilterChain wrapped = chain;
        for (int i = filters.size() - 1; i >= 0; i--) {
            Filter f = filters.get(i);
            FilterChain next = wrapped;
            wrapped = (req, res) -> {
                try {
                    f.doFilter((HttpServletRequest) req, (HttpServletResponse) res, next);
                } catch (Exception e) {
                    log.error("[Filter:{}] Plugin filter error — skipping: {}",
                            position, f.getClass().getSimpleName(), e);
                    next.doFilter(req, res);
                }
            };
        }
        wrapped.doFilter(request, response);
    }
}
