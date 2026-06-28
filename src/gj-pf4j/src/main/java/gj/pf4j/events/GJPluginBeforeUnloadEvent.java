/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.events;

import gj.pf4j.descriptor.GJPluginDescriptor;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

public class GJPluginBeforeUnloadEvent extends ApplicationEvent {

    private final GJPluginDescriptor descriptor;

    @Serial
    private static final long serialVersionUID = 8182395013185023581L;

    public GJPluginBeforeUnloadEvent(GJPluginDescriptor descriptor) {
        super(descriptor.getPluginId());
        this.descriptor = descriptor;
    }

    public GJPluginDescriptor getPluginDescriptor() {
        return descriptor;
    }

    public String getPluginId() {
        return descriptor.getPluginId();
    }
}
