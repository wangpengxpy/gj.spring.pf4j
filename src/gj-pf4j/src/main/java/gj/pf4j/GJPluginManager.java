/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import gj.pf4j.descriptor.GJPluginDescriptor;
import gj.pf4j.descriptor.GJPropertiesPluginDescriptorFinder;
import gj.pf4j.events.GJPluginStartedEvent;
import gj.pf4j.events.GJPluginStartingError;

import lombok.Getter;
import lombok.Setter;
import org.pf4j.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class GJPluginManager extends DefaultPluginManager implements ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(GJPluginManager.class);

    private final Path pluginsRoot;

    @Setter
    @Getter
    private boolean mainApplicationStarted;
    private GenericApplicationContext mainApplicationContext;
    @Getter
    private boolean autoStartPlugin = true;
    private PluginRepository pluginRepository;
    private ConfigurationRepository configurationRepository;
    private final Map<String, GJPluginStartingError> startingErrors = new HashMap<>();

    public GJPluginManager(Path pluginsRoot) {
        super(pluginsRoot);
        this.pluginsRoot = pluginsRoot;
    }

    @Override
    protected ExtensionFactory createExtensionFactory() {
        return new GJPluginExtensionFactory(this);
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        Objects.requireNonNull(applicationContext, "applicationContext must not be null");
        this.mainApplicationContext = (GenericApplicationContext) applicationContext;
    }

    @Override
    protected PluginDescriptorFinder createPluginDescriptorFinder() {
        return new CompoundPluginDescriptorFinder()
                .add(new GJPropertiesPluginDescriptorFinder())
                .add(new ManifestPluginDescriptorFinder());
    }

    @Override
    protected PluginRepository createPluginRepository() {
        this.pluginRepository = new GJJarPluginRepository(this.getPluginsRoots());
        return this.pluginRepository;
    }

    protected ConfigurationRepository createConfigurationRepository() {
        String configDir = System.getProperty(PLUGINS_DIR_CONFIG_PROPERTY_NAME);
        Path configPath = configDir != null
                ? Paths.get(configDir)
                : getPluginsRoots().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No pluginsRoot configured"));

        return new GJPluginConfigurationRepository(configPath);
    }

    public ApplicationContext getMainApplicationContext() {
        return mainApplicationContext;
    }

    public void init() {
        loadPlugins();
    }

    private void doStartPlugins() {
        startingErrors.clear();
        long ts = System.currentTimeMillis();

        List<PluginWrapper> sortedPlugins = resolvedPlugins.stream()
                .sorted(Comparator.comparingInt(pw -> {
                    if (pw.getDescriptor() instanceof GJPluginDescriptor gd) {
                        return gd.getOrder();
                    }
                    return 100000;
                }))
                .toList();

        for (PluginWrapper pluginWrapper : sortedPlugins) {
            PluginState pluginState = pluginWrapper.getPluginState();
            if ((PluginState.DISABLED != pluginState) && (PluginState.STARTED != pluginState)) {
                try {
                    pluginWrapper.getPlugin().start();
                    pluginWrapper.setPluginState(PluginState.STARTED);
                    startedPlugins.add(pluginWrapper);
                    firePluginStateEvent(new PluginStateEvent(this, pluginWrapper, pluginState));
                    GJSpringPlugin springPlugin = (GJSpringPlugin) pluginWrapper.getPlugin();
                    springPlugin.getApplicationContext().publishEvent(new GJPluginStartedEvent(pluginWrapper.getPluginId(), springPlugin));
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    startingErrors.put(pluginWrapper.getPluginId(), new GJPluginStartingError(
                            pluginWrapper.getPluginId(), e.getMessage(), e.toString()));
                }
            }
        }

        long duration = System.currentTimeMillis() - ts;

        List<String> startedPluginIds = startedPlugins.stream()
                .map(PluginWrapper::getPluginId)
                .toList();

        // Log INFO level information for successfully started plugins
        log.info("[PF4J] {} plugins are started in {}ms. {} failed. Started plugins: [{}]",
                startedPluginIds.size(),
                duration,
                startingErrors.size(),
                String.join(", ", startedPluginIds));

        // Log ERROR-level detailed exception information for plugins that failed to start
        if (!startingErrors.isEmpty()) {
            log.error("[PF4J] Plugin startup failures ({}):", startingErrors.size());
            PluginErrors();
        }
    }

    private void doStopPlugins() {
        startingErrors.clear();
        // stop started plugins in reverse order
        Collections.reverse(startedPlugins);
        Iterator<PluginWrapper> itr = startedPlugins.iterator();
        while (itr.hasNext()) {
            PluginWrapper pluginWrapper = itr.next();
            PluginState pluginState = pluginWrapper.getPluginState();
            if (PluginState.STARTED == pluginState) {
                try {
                    log.info("Stop plugin '{}'", getPluginLabel(pluginWrapper.getDescriptor()));
                    pluginWrapper.getPlugin().stop();
                    pluginWrapper.setPluginState(PluginState.STOPPED);
                    itr.remove();
                    firePluginStateEvent(new PluginStateEvent(this, pluginWrapper, pluginState));
                } catch (PluginRuntimeException e) {
                    log.error(e.getMessage(), e);
                    startingErrors.put(pluginWrapper.getPluginId(), new GJPluginStartingError(
                            pluginWrapper.getPluginId(), e.getMessage(), e.toString()));
                }
            }
        }

        // Log ERROR-level detailed exception information for plugins that failed to stop
        if (!startingErrors.isEmpty()) {
            log.error("[PF4J] Plugin stopped failures ({}):", startingErrors.size());
            PluginErrors();
        }
    }

    private void PluginErrors() {
        int index = 1;
        for (Map.Entry<String, GJPluginStartingError> entry : startingErrors.entrySet()) {
            String pluginId = entry.getKey();
            GJPluginStartingError error = entry.getValue();
            log.error(
                    """
                            [PF4J] Failure #{}:
                              Plugin ID : {}
                              Error     : {}
                              Detail    : {}""",
                    index++,
                    pluginId,
                    error.getErrorMessage(),
                    error.getErrorDetail() != null ? error.getErrorDetail() : "N/A"
            );
        }
    }

    private PluginState doStartPlugin(String pluginId, boolean sendEvent) {
        PluginWrapper pluginWrapper = getPlugin(pluginId);
        if (pluginWrapper == null) {
            log.info("Plugin already unloaded or not found: {}", pluginId);
            return PluginState.UNLOADED;
        }
        PluginState previousState = pluginWrapper.getPluginState();
        if (previousState.isStarted()) {
            log.info("Already started plugin '{}'", pluginId);
            return PluginState.STARTED;
        }

        if (!resolvedPlugins.contains(pluginWrapper)) {
            log.warn("Cannot start an unresolved plugin '{}'", getPluginLabel(pluginWrapper.getDescriptor()));
            return previousState;
        }

        for (PluginDependency dependency : pluginWrapper.getDescriptor().getDependencies()) {
            // start dependency only if it marked as required (non-optional) or if it optional and loaded
            if (!dependency.isOptional() || plugins.containsKey(dependency.getPluginId())) {
                startPlugin(dependency.getPluginId());
            }
        }
        try {
            PluginState pluginState = super.startPlugin(pluginId);
            if (sendEvent && previousState != pluginState) {
                GJSpringPlugin springPlugin = (GJSpringPlugin) pluginWrapper.getPlugin();
                springPlugin.getApplicationContext().publishEvent(new GJPluginStartedEvent(pluginWrapper.getPluginId(), springPlugin));
            }
            return pluginState;
        } catch (Exception e) {
            log.error("Plugin start failed：'{}',error message：{}", pluginId, e.getMessage());
            startingErrors.put(pluginWrapper.getPluginId(), new GJPluginStartingError(
                    pluginWrapper.getPluginId(), e.getMessage(), e.toString()));
        }
        return pluginWrapper.getPluginState();
    }

    private PluginState doStopPlugin(String pluginId, boolean sendEvent) {
        PluginWrapper pluginWrapper = getPlugin(pluginId);
        if (pluginWrapper == null) {
            log.info("Plugin already unloaded or not found: '{}'", pluginId);
            return PluginState.UNLOADED;
        }
        PluginState previousState = pluginWrapper.getPluginState();
        if (previousState.isStopped()) {
            log.info("Already stopped plugin '{}'", pluginId);
            return PluginState.STOPPED;
        }

        //stopDependents
        List<String> dependents = dependencyResolver.getDependents(pluginId);
        while (!dependents.isEmpty()) {
            String dependent = dependents.remove(0);
            super.stopPlugin(dependent);
            dependents.addAll(0, dependencyResolver.getDependents(dependent));
        }

        try {
            PluginState pluginState = super.stopPlugin(pluginId);
            if (sendEvent && previousState != pluginState) {
                GJSpringPlugin springPlugin = (GJSpringPlugin) pluginWrapper.getPlugin();
                springPlugin.getApplicationContext().publishEvent(new GJPluginStartedEvent(pluginWrapper.getPluginId(), springPlugin));
            }
            return pluginState;
        } catch (Exception e) {
            log.error("Plugin stopped failed：'{}',error message：{}", pluginId, e.getMessage());
            startingErrors.put(pluginWrapper.getPluginId(), new GJPluginStartingError(
                    pluginWrapper.getPluginId(), e.getMessage(), e.toString()));
        }
        return pluginWrapper.getPluginState();
    }

    @Override
    public void startPlugins() {
        doStartPlugins();
    }

    @Override
    public PluginState startPlugin(String pluginId) {
        return doStartPlugin(pluginId, true);
    }

    @Override
    public void stopPlugins() {
        doStopPlugins();
    }

    @Override
    public PluginState stopPlugin(String pluginId) {
        return doStopPlugin(pluginId, true);
    }

    public void restartPlugins() {
        doStopPlugins();
        startPlugins();
    }

    public PluginState restartPlugin(String pluginId) {
        PluginState pluginState = doStopPlugin(pluginId, false);
        if (pluginState != PluginState.STARTED) doStartPlugin(pluginId, false);
        pluginState = doStartPlugin(pluginId, false);
        return pluginState;
    }

    public void reloadPlugins(boolean restartStartedOnly) {
        doStopPlugins();
        List<String> startedPluginIds = new ArrayList<>();
        getPlugins().forEach(plugin -> {
            if (plugin.getPluginState() == PluginState.STARTED) {
                startedPluginIds.add(plugin.getPluginId());
            }
            unloadPlugin(plugin.getPluginId());
        });
        loadPlugins();
        if (restartStartedOnly) {
            startedPluginIds.forEach(pluginId -> {
                // restart started plugin
                if (getPlugin(pluginId) != null) {
                    doStartPlugin(pluginId, false);
                }
            });
        } else {
            startPlugins();
        }
    }

    public PluginState reloadPlugin(String pluginId) {
        PluginWrapper pluginWrapper = getPlugin(pluginId);
        if (pluginWrapper == null) {
            try {
                Path pluginDir = pluginsRoot.resolve(pluginId);
                try (Stream<Path> paths = Files.list(pluginDir)) {
                    Path pluginPath = paths
                            .filter(Files::isRegularFile)
                            .filter(path -> {
                                String fileName = path.getFileName().toString();
                                return fileName.startsWith(pluginId + "-") && path.toString().endsWith(".jar");
                            })
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "No JAR file found in plugin directory: " + pluginDir));
                    loadPlugin(pluginPath);
                    return startPlugin(pluginId);
                } catch (IOException e) {
                    log.error("Plugin failed to reload: {}", pluginId, e);
                    throw new RuntimeException("Failed to list plugin directory: " + pluginDir, e);
                }
            } catch (Exception ex) {
                log.error("Plugin reload '{}' failed", pluginId, ex);
                throw new RuntimeException("Failed to reload plugin: " + pluginId, ex);
            }
        }
        PluginState previousState = pluginWrapper.getPluginState();
        if (previousState == PluginState.RESOLVED || previousState == PluginState.STOPPED) {
            return doStartPlugin(pluginId, true);
        } else if (previousState == PluginState.STARTED) {
            doStopPlugin(pluginId, false);
            return doStartPlugin(pluginId, true);
        }
        log.error("Plugin reload '{}' unexpected error for state '{}'", pluginId, previousState);
        return null;
    }

    @Override
    protected void initialize() {
        super.initialize();
        this.resolveRecoveryStrategy = ResolveRecoveryStrategy.IGNORE_PLUGIN_AND_CONTINUE;
        this.configurationRepository = createConfigurationRepository();
    }

    @Override
    protected PluginLoader createPluginLoader() {
        return new GJJarPluginLoader(this);
    }

    @Override
    protected PluginFactory createPluginFactory() {
        return new GJPluginFactory();
    }
}
