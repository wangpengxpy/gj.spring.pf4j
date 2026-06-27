/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("gj.plugin")
public class GJPluginProperties {

    private HotReload hotReload = HotReload.WATCH;

    private String pluginDir;

    public HotReload getHotReload() {
        return hotReload;
    }

    public void setHotReload(HotReload hotReload) {
        this.hotReload = hotReload;
    }

    public String getPluginDir() {
        return pluginDir;
    }

    public void setPluginDir(String pluginDir) {
        this.pluginDir = pluginDir;
    }

    public static final String DEFAULT_PLUGIN_DIR = "plugins";

    public enum HotReload {
        MANUAL,
        WATCH
    }
}
