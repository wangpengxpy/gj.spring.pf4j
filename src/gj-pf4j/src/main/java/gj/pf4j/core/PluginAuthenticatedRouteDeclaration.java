/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.core;

/**
 * A plugin's declaration of a plugin-authenticated (OR-auth) route.
 * Used by {@code GJRouterFunctions} to collect authenticated paths
 * during functional route definition.
 *
 * @param pathPattern Ant-style path pattern (e.g. {@code /api/payment/**})
 * @param httpMethod  HTTP method ({@code GET}, {@code POST}, {@code *}, etc.)
 */
public record PluginAuthenticatedRouteDeclaration(
        String pathPattern,
        String httpMethod
) {}
