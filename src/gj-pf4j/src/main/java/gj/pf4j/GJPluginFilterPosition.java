/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

/**
 * Seven insertion positions in the Spring Security filter chain.
 * Plugins declare filters at one of these positions; the host
 * decides (via configuration) which positions are enabled.
 * <p>
 * {@code AUTHENTICATION} is occupied by the framework's
 * {@code IPluginAuthenticationProvider} chain — plugins do not
 * implement a filter interface at this position.
 */
public enum GJPluginFilterPosition {
    FIRST,
    SESSION_RESTORE,
    FORM_LOGIN,
    AUTHENTICATION,
    ANONYMOUS,
    PRE_AUTHORIZE,
    LAST
}
