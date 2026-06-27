/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.events;

import gj.pf4j.GJPluginManager;
import gj.pf4j.hotreload.GJPluginHotReloadManager;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class GJMainApplicationStartedListener implements ApplicationListener<ContextRefreshedEvent> {

    private final GJPluginManager pluginManager;

    private final ApplicationContext applicationContext;

    private final GJPluginHotReloadManager hotReloadManager;

    public GJMainApplicationStartedListener(ApplicationContext applicationContext,
                                             GJPluginManager pluginManager,
                                             GJPluginHotReloadManager hotReloadManager) {
        this.applicationContext = applicationContext;
        this.pluginManager = pluginManager;
        this.hotReloadManager = hotReloadManager;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        if (context.getParent() != null) {
            return;
        }
        if (pluginManager.isAutoStartPlugin()) {
            pluginManager.loadPlugins();
            pluginManager.startPlugins();
        }
        pluginManager.setMainApplicationStarted(true);

        hotReloadManager.startWatching();
    }
}
