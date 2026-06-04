/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.events;

import gj.pf4j.GJSpringPlugin;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

public class GJPluginStartedEvent extends ApplicationEvent {

    private final GJSpringPlugin springPlugin;

    @Serial
    private static final long serialVersionUID = 1651490578605729784L;

    public GJPluginStartedEvent(Object source, GJSpringPlugin springPlugin) {
        super(source);
        this.springPlugin = springPlugin;
    }

    public GJSpringPlugin getSpringPlugin() {
        return springPlugin;
    }
}
