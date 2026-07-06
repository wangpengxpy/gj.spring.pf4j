/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

import gj.pf4j.GJPluginFilterPosition;
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
@ConfigurationProperties(prefix = "plugin.security")
public class PluginFilterConfigProperties {

    private FilterConfig filter = new FilterConfig();
    private final Map<String, PluginSecurityConfig> plugins = new HashMap<>();

    public FilterConfig getFilter() { return filter; }
    public void setFilter(FilterConfig filter) { this.filter = filter; }
    public Map<String, PluginSecurityConfig> getPlugins() { return plugins; }
    public void setPlugins(Map<String, PluginSecurityConfig> plugins) {
        this.plugins.clear();
        if (plugins != null) this.plugins.putAll(plugins);
    }

    public static class FilterConfig {
        private boolean enabled = false;
        private Set<GJPluginFilterPosition> allowedPositions = Collections.emptySet();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Set<GJPluginFilterPosition> getAllowedPositions() { return allowedPositions; }
        public void setAllowedPositions(Set<GJPluginFilterPosition> allowedPositions) {
            this.allowedPositions = allowedPositions != null
                    ? allowedPositions : Collections.emptySet();
        }
    }

    public static class PluginSecurityConfig {
        private FilterConfig filter = new FilterConfig();

        public FilterConfig getFilter() { return filter; }
        public void setFilter(FilterConfig filter) { this.filter = filter; }
    }
}
