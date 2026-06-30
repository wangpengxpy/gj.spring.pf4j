/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import gj.pf4j.events.GJPluginRestartedEvent;
import gj.pf4j.events.GJPluginStartingEvent;
import gj.pf4j.events.GJPluginStoppedEvent;
import gj.pf4j.lifecycle.PluginLifecycleEngine;
import gj.pf4j.lifecycle.PluginLifecyclePhase;
import gj.pf4j.lifecycle.PluginResourceRegistrar;
import org.pf4j.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

public final class GJSpringPlugin extends Plugin {

    private static final Logger log = LoggerFactory.getLogger(GJSpringPlugin.class);

    private final GJPlugin plugin;

    private final GJPluginContext pluginContext;

    private final GenericApplicationContext mainAppCtx;

    private final PluginLifecycleEngine engine;

    private ApplicationContext applicationContext;

    public GJSpringPlugin(GJPluginContext pluginContext, GJPlugin plugin,
                          GenericApplicationContext mainApplicationContext,
                          List<PluginResourceRegistrar> programmaticRegistrars) {
        this.pluginContext = pluginContext;
        this.plugin = plugin;
        this.mainAppCtx = mainApplicationContext;
        this.engine = PluginLifecycleEngine.create(mainApplicationContext,
                programmaticRegistrars != null ? programmaticRegistrars : Collections.emptyList());
    }

    @Override
    public void start() {
        long startTs = System.currentTimeMillis();
        log.info("Starting plugin '{}' ......", pluginContext.getPluginId());

        // register plugin application context
        applicationContext = createApplicationContext();
        pluginContext.setApplicationContext(applicationContext);

        applicationContext.publishEvent(
                new GJPluginStartingEvent(applicationContext, pluginContext.getDescriptor()));

        if (pluginContext.isEverStarted()) {
            applicationContext.publishEvent(
                    new GJPluginRestartedEvent(pluginContext.getDescriptor()));
        }

        log.info("Plugin '{}' is started in {}ms", pluginContext.getPluginId(), System.currentTimeMillis() - startTs);
    }

    @Override
    public void stop() {
        if (applicationContext == null) {
            log.warn("Plugin '{}' stop() called but ApplicationContext is null " +
                    "(start may have failed), nothing to stop.", pluginContext.getPluginId());
            return;
        }

        log.info("Stopping plugin '{}' ......", pluginContext.getPluginId());

        engine.executePhase(PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE, (AnnotationConfigApplicationContext) applicationContext);

        applicationContext.publishEvent(
                new GJPluginStoppedEvent(applicationContext, pluginContext.getDescriptor()));

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
        engine.executePhase(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH, context);
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
        engine.executePhase(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH, context);
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
        if (context.isActive()) {
            return;
        }
        try {
            context.refresh();
        } catch (Exception e) {
            throw new GJPluginException(
                    "[Plugin: " + pluginContext.getPluginId() + "] Startup failed: " + e.getMessage(), e);
        }
    }

    private AnnotationConfigApplicationContext preCreateApplicationContext() {
        final String pluginId = pluginContext.getPluginId();
        String packageName = plugin.getClass().getPackageName();
        if (!StringUtils.hasLength(packageName)) {
            throw new GJPluginException(
                    "Plugin class '" + plugin.getClass().getName() + "' is in the default package. "
                    + "GJPlugin subclasses must be placed under a named package that matches plugin.id from plugin.properties."
            );
        }
        if (!packageName.equals(pluginId)) {
            throw new GJPluginException(
                    "Plugin package mismatch: plugin.id from plugin.properties is '" + pluginId
                    + "', but GJPlugin subclass '" + plugin.getClass().getName()
                    + "' is in package '" + packageName + "'. "
                    + "The plugin.id must exactly match the package name of the GJPlugin subclass."
            );
        }
        // Initialize the plugin context
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
        // Set a custom Bean name generator to prevent conflicts between multiple plugins
        int lastDotIndex = pluginId.lastIndexOf('.');
        if (lastDotIndex < 0) {
            throw new GJPluginException(
                    "plugin.id '" + pluginId + "' is not a fully qualified name. "
                    + "plugin.id must contain at least one dot (e.g. 'com.example.myplugin'), "
                    + "because its last segment is used as the plugin's bean name prefix."
            );
        }
        final String home = pluginId.substring(lastDotIndex + 1);
        applicationContext.setBeanNameGenerator(
                new GJPluginBeanNameGenerator(home));
        // Set the property processor to enable @ConfigurationProperties in the plugin
        ConfigurationPropertiesBindingPostProcessor.register(applicationContext);
        // Carry pluginId via Spring's ApplicationContext.setId(). This field is only
        // used by toString() in AbstractApplicationContext — it has no effect on Spring
        // lifecycle or bean resolution logic. Registrars retrieve it via ctx.getId().
        applicationContext.setId(pluginId);
        // Set the parent context (inherit Beans from the main application)
        applicationContext.setParent(mainAppCtx);
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
    GJPluginContext getPluginContext() {
        return pluginContext;
    }
}
