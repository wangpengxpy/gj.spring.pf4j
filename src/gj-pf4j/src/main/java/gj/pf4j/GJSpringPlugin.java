/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import gj.pf4j.events.GJPluginRestartedEvent;
import gj.pf4j.events.GJPluginStartingEvent;
import gj.pf4j.events.GJPluginStoppedEvent;
import org.pf4j.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;

public final class GJSpringPlugin extends Plugin {

    private static final Logger log = LoggerFactory.getLogger(GJSpringPlugin.class);

    private final GJPlugin plugin;

    private final GJPluginContext pluginContext;

    private final GJPluginLifecycle lifecycle;

    private ApplicationContext applicationContext;

    public GJSpringPlugin(GJPluginContext pluginContext, GJPlugin plugin) {
        this.pluginContext = pluginContext;
        this.plugin = plugin;
        lifecycle = new GJPluginLifecycle(pluginContext);
    }

    @Override
    public void start() {
        long startTs = System.currentTimeMillis();
        log.info("Starting plugin '{}' ......", pluginContext.getPluginId());

        // register plugin application context
        applicationContext = createApplicationContext();
        pluginContext.setApplicationContext(applicationContext);

        applicationContext.publishEvent(new GJPluginStartingEvent(applicationContext, this));

        if (pluginContext.isEverStarted()) {
            applicationContext.publishEvent(new GJPluginRestartedEvent(applicationContext));
        }

        log.info("Plugin '{}' is started in {}ms", pluginContext.getPluginId(), System.currentTimeMillis() - startTs);
    }

    @Override
    public void stop() {
        log.info("Stopping plugin '{}' ......", pluginContext.getPluginId());

        lifecycle.unregisterPluginResources();

        applicationContext.publishEvent(new GJPluginStoppedEvent(applicationContext, this));

        ((ConfigurableApplicationContext) applicationContext).close();

        log.info("Plugin '{}' is stopped", pluginContext.getPluginId());
    }

    private ApplicationContext createApplicationContext() {
        long startTs = System.currentTimeMillis();
        final String pluginId = pluginContext.getPluginId();
        // Step 1: Pre-create application context
        log.info("Initializing base context for plugin '{}'", pluginId);
        long preCreateStart = System.currentTimeMillis();
        AnnotationConfigApplicationContext annotationContext = preCreateApplicationContext();
        log.info("Initialized base context for plugin '{}' in {} ms",
                pluginId, System.currentTimeMillis() - preCreateStart);
        // Step 2: Customize context before refresh
        log.info("Customizing context configuration for plugin '{}'", pluginId);
        long handleStart = System.currentTimeMillis();
        AnnotationConfigApplicationContext context = plugin.beforeApplicationContextRefresh(annotationContext);
        log.info("Customized context configuration for plugin '{}' in {} ms",
                pluginId, System.currentTimeMillis() - handleStart);
        if (context == null) {
            context = annotationContext;
        }
        // Step 3: registerPluginResources
        lifecycle.registerPluginResources(context);
        // Step 4: Refresh the context (load beans, etc.)
        log.info("Refreshing Spring context for plugin '{}'", pluginId);
        long postCreateStart = System.currentTimeMillis();
        postCreateApplicationContext(context);
        log.info("Refreshed Spring context for plugin '{}' in {} ms",
                pluginId, System.currentTimeMillis() - postCreateStart);
        // Step 5: Post-refresh custom logic
        log.info("Executing post-refresh logic for plugin '{}'", pluginId);
        long customStart = System.currentTimeMillis();
        plugin.afterApplicationContextReady(context);
        pluginContext.setApplicationContext(context);
        lifecycle.registerPostStartResources(context);
        log.info("Completed post-refresh logic for plugin '{}' in {} ms",
                pluginId, System.currentTimeMillis() - customStart);
        // Total time
        log.info("Plugin '{}' context fully initialized in {} ms",
                pluginId, System.currentTimeMillis() - startTs);
        return context;
    }

    private void postCreateApplicationContext(AnnotationConfigApplicationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("AnnotationConfigApplicationContext cannot be null");
        }
        // Context is already active, no need to refresh.
        if (context.isActive()) {
            return;
        }
        context.refresh();
    }

    private AnnotationConfigApplicationContext preCreateApplicationContext() {
        final String pluginId = pluginContext.getPluginId();
        String packageName = plugin.getClass().getPackageName();
        if (!StringUtils.hasLength(packageName) || !packageName.equals(pluginId)) {
            throw new IllegalStateException(
                    "Plugin configuration does not comply with the convention rules:\n" +
                            "1. The plugin.id must be exactly consistent with the plugin package name;\n" +
                            "2. Plugin classes that inherit from GJPlugin must be located under the package directory specified by plugin.id.\n" +
                            "Current plugin.id = '" + pluginId + "', but the actual parsed package name of the plugin class = '" + packageName + "'."
            );
        }
        // Initialize the plugin context
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
        // Set a custom Bean name generator to prevent conflicts between multiple plugins
        int lastDotIndex = pluginId.lastIndexOf('.');
        if (lastDotIndex < 0) {
            throw new IllegalArgumentException("Plugin Id is not a valid fully qualified name: " + pluginId);
        }
        final String home = pluginId.substring(lastDotIndex + 1);
        applicationContext.setBeanNameGenerator(
                new GJPluginBeanNameGenerator(home));
        // Set the property processor to enable @ConfigurationProperties in the plugin
        ConfigurationPropertiesBindingPostProcessor.register(applicationContext);
        // Set the parent context (inherit Beans from the main application)
        applicationContext.setParent(pluginContext.getMainApplicationContext());
        // Set the plugin classLoader
        applicationContext.setClassLoader(pluginContext.getClassLoader());
        // Scan the plugin package
        applicationContext.scan(packageName);
        return applicationContext;
    }

    @NonNull
    public ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext is not available");
        }
        return applicationContext;
    }

    @NonNull
    public GJPluginContext getPluginContext() {
        return pluginContext;
    }
}