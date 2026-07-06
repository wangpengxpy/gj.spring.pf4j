/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.core;

/**
 * A plugin's declaration of a single anonymous route.
 * Used by {@code GJRouterFunctions} to collect anonymous paths
 * during functional route definition.
 *
 * @param pathPattern Ant-style path pattern (e.g. {@code /api/public/**})
 * @param httpMethod  HTTP method ({@code GET}, {@code POST}, {@code *}, etc.)
 * @param reason      explanation for anonymous access (for auditing)
 */
public record AnonymousRouteDeclaration(
        String pathPattern,
        String httpMethod,
        String reason
) {}
