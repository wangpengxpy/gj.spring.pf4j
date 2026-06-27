/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.events;

import gj.pf4j.GJSpringPlugin;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

public class GJPluginAfterInstallEvent extends ApplicationEvent {

    private final String pluginId;
    private final GJSpringPlugin springPlugin;

    @Serial
    private static final long serialVersionUID = 3641250761898129462L;

    public GJPluginAfterInstallEvent(String pluginId, GJSpringPlugin springPlugin) {
        super(pluginId);
        this.pluginId = pluginId;
        this.springPlugin = springPlugin;
    }

    public String getPluginId() {
        return pluginId;
    }

    public GJSpringPlugin getSpringPlugin() {
        return springPlugin;
    }
}
