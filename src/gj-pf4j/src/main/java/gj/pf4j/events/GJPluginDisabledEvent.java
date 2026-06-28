/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.events;

import gj.pf4j.descriptor.GJPluginDescriptor;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

public class GJPluginDisabledEvent extends ApplicationEvent {

    private final GJPluginDescriptor descriptor;

    @Serial
    private static final long serialVersionUID = 7291546013184820493L;

    public GJPluginDisabledEvent(Object source, GJPluginDescriptor descriptor) {
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
