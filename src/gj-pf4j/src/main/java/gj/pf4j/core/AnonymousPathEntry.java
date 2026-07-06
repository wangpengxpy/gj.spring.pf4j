/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.core;

import java.time.LocalDateTime;

/**
 * Describes a single anonymous access entry registered by a plugin controller.
 *
 * @param pluginId        the plugin that registered this entry
 * @param pathPattern     URL path pattern (e.g. {@code /api/v3/sso/{id}/callback})
 * @param httpMethod      HTTP method ({@code GET}, {@code POST}, etc.) or {@code *} for any method
 * @param controllerClass fully qualified controller class name
 * @param methodName      controller method name
 * @param reason          explanation for anonymous access (from {@link AllowAnonymous#reason()})
 * @param registeredAt    timestamp when the entry was registered
 */
public record AnonymousPathEntry(
        String pluginId,
        String pathPattern,
        String httpMethod,
        String controllerClass,
        String methodName,
        String reason,
        LocalDateTime registeredAt
) {}
