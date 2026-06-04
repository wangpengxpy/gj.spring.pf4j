/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.events;

import gj.pf4j.GJSpringPlugin;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

public class GJPluginStoppedEvent extends ApplicationEvent {

    private final GJSpringPlugin springPlugin;

    @Serial
    private static final long serialVersionUID = 1048404352252169025L;

    public GJPluginStoppedEvent(Object source, GJSpringPlugin springPlugin) {
        super(source);
        this.springPlugin = springPlugin;
    }

    public GJSpringPlugin getSpringPlugin() {
        return springPlugin;
    }
}
