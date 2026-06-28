/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.events;

import gj.pf4j.descriptor.GJPluginDescriptor;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

public class GJPluginAfterInstallEvent extends ApplicationEvent {

    private final GJPluginDescriptor descriptor;

    @Serial
    private static final long serialVersionUID = 3641250761898129462L;

    public GJPluginAfterInstallEvent(GJPluginDescriptor descriptor) {
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
