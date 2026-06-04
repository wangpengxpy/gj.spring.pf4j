/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.events;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

public class GJPluginRestartedEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1651490578605729784L;

    public GJPluginRestartedEvent(ApplicationContext pluginApplicationContext) {
        super(pluginApplicationContext);
    }
}
