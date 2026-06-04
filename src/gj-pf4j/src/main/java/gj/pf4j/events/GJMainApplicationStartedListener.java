/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.events;

import gj.pf4j.GJPluginManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component
public class GJMainApplicationStartedListener implements ApplicationListener<ContextRefreshedEvent> {

    private final GJPluginManager pluginManager;

    private final ApplicationContext applicationContext;

    public GJMainApplicationStartedListener(ApplicationContext applicationContext, GJPluginManager pluginManager) {
        this.applicationContext = applicationContext;
        this.pluginManager = pluginManager;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        if (context.getParent() != null) {
            return;
        }
        if (pluginManager.isAutoStartPlugin()) {
            pluginManager.startPlugins();
        }
        pluginManager.setMainApplicationStarted(true);
    }
}
