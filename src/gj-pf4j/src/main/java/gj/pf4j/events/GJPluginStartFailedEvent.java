/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.events;

import gj.pf4j.descriptor.GJPluginDescriptor;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

public class GJPluginStartFailedEvent extends ApplicationEvent {

    private final GJPluginDescriptor descriptor;

    private final GJPluginStartingError error;

    @Serial
    private static final long serialVersionUID = -8140281458116119936L;

    public GJPluginStartFailedEvent(Object source, GJPluginDescriptor descriptor,
                                     GJPluginStartingError error) {
        super(source);
        this.descriptor = descriptor;
        this.error = error;
    }

    public GJPluginDescriptor getPluginDescriptor() {
        return descriptor;
    }

    public String getPluginId() {
        return descriptor.getPluginId();
    }

    public GJPluginStartingError getError() {
        return error;
    }
}
