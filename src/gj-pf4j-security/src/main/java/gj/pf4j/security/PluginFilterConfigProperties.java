/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

import gj.pf4j.GJPluginFilterPosition;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.*;

/**
 * Host-level configuration for plugin filter extension points.
 * <p>
 * Follows a three-tier control model (inspired by Kong's
 * "install ≠ enable"):
 * <ol>
 *   <li>Global on/off switch</li>
 *   <li>Per-position allow list</li>
 *   <li>Per-plugin position authorization</li>
 * </ol>
 * <p>
 * All filter positions default to <strong>disabled</strong>.
 */
@Getter
@ConfigurationProperties(prefix = "gj.security")
public class PluginFilterConfigProperties {

    @Setter
    private FilterConfig filter = new FilterConfig();

    private final Map<String, PluginSecurityConfig> plugins = new HashMap<>();

    public void setPlugins(Map<String, PluginSecurityConfig> plugins) {
        this.plugins.clear();
        if (plugins != null) this.plugins.putAll(plugins);
    }

    @Getter
    @Setter
    public static class FilterConfig {
        private boolean enabled = false;

        private Set<GJPluginFilterPosition> allowedPositions = Collections.emptySet();

        public void setAllowedPositions(Set<GJPluginFilterPosition> allowedPositions) {
            this.allowedPositions = allowedPositions != null
                    ? allowedPositions : Collections.emptySet();
        }
    }

    @Getter
    @Setter
    public static class PluginSecurityConfig {
        private FilterConfig filter = new FilterConfig();
    }
}
