/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.events;

import gj.pf4j.GJSpringPlugin;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

public class GJPluginBeforeUnloadEvent extends ApplicationEvent {

    private final String pluginId;
    private final GJSpringPlugin springPlugin;

    @Serial
    private static final long serialVersionUID = 8182395013185023581L;

    public GJPluginBeforeUnloadEvent(String pluginId, GJSpringPlugin springPlugin) {
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
