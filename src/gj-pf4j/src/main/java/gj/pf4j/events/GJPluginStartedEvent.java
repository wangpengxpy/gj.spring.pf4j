/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.events;

import gj.pf4j.descriptor.GJPluginDescriptor;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

public class GJPluginStartedEvent extends ApplicationEvent {

    private final GJPluginDescriptor descriptor;

    @Serial
    private static final long serialVersionUID = 1651490578605729784L;

    public GJPluginStartedEvent(Object source, GJPluginDescriptor descriptor) {
        super(source);
        this.descriptor = descriptor;
    }

    public GJPluginDescriptor getPluginDescriptor() {
        return descriptor;
    }

    public String getPluginId() {
        return descriptor.getPluginId();
    }
}
