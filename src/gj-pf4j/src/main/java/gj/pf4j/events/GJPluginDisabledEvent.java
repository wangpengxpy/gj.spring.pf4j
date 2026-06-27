/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.events;

import org.springframework.context.ApplicationEvent;

import java.io.Serial;

public class GJPluginDisabledEvent extends ApplicationEvent {

    private final String pluginId;

    @Serial
    private static final long serialVersionUID = 7291546013184820493L;

    public GJPluginDisabledEvent(Object source, String pluginId) {
        super(source);
        this.pluginId = pluginId;
    }

    public String getPluginId() {
        return pluginId;
    }
}
