/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.hotreload;

import gj.pf4j.GJPluginProperties;
import gj.pf4j.GJPluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class GJPluginHotReloadManager {

    private static final Logger log = LoggerFactory.getLogger(GJPluginHotReloadManager.class);

    private final GJPluginService pluginService;
    private final Path pluginsDir;
    private final GJPluginProperties.HotReload mode;
    private final WatchService watchService;
    private final Map<String, WatchKey> watchKeys = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> debounceTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "gj-hot-reload-debounce");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running;

    public GJPluginHotReloadManager(GJPluginService pluginService, Path pluginsDir,
                                     GJPluginProperties properties) throws IOException {
        this.pluginService = pluginService;
        this.pluginsDir = pluginsDir;
        this.mode = properties.getHotReload();
        this.watchService = FileSystems.getDefault().newWatchService();
    }

    public void startWatching() {
        if (mode != GJPluginProperties.HotReload.WATCH) {
            log.info("[HotReload] Mode is manual, WatchService not started");
            return;
        }
        running = true;
        Thread watcher = new Thread(this::watchLoop, "gj-hot-reload-watcher");
        watcher.setDaemon(true);
        watcher.start();
        log.info("[HotReload] WatchService started, monitoring: {}", pluginsDir);

        // Register existing plugin directories
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(pluginsDir, Files::isDirectory)) {
            for (Path dir : dirs) {
                String pluginId = dir.getFileName().toString();
                registerPluginWatchKey(pluginId);
            }
        } catch (IOException e) {
            log.error("[HotReload] Initial plugin directory scan failed: {}", e.getMessage());
        }
    }

    private void watchLoop() {
        // Also register the parent directory for new plugin dirs
        try {
            pluginsDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE);
        } catch (IOException e) {
            log.error("[HotReload] Failed to register plugins/ watch: {}", e.getMessage());
            return;
        }

        while (running) {
            WatchKey key;
            try {
                key = watchService.poll(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            }
            if (key == null) continue;

            Path watchDir = (Path) key.watchable();
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                Path name = (Path) event.context();
                Path fullPath = watchDir.resolve(name);

                if (watchDir.equals(pluginsDir)) {
                    // New or deleted plugin subdirectory
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        if (Files.isDirectory(fullPath)) {
                            String pluginId = name.toString();
                            log.info("[HotReload] New plugin directory detected: {}", pluginId);
                            registerPluginWatchKey(pluginId);
                            scheduleDebounce(pluginId);
                        }
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        String pluginId = name.toString();
                        // Only react if this was a known plugin directory (not a stray JAR)
                        if (watchKeys.containsKey(pluginId)) {
                            log.info("[HotReload] Plugin directory deleted: {}", pluginId);
                            scheduleDebounce(pluginId);
                        } else {
                            log.debug("[HotReload] Ignored non-directory deletion in plugins/: {}", name);
                        }
                    }
                } else {
                    // Inside a plugin directory — JAR change
                    String pluginId = watchDir.getFileName().toString();
                    String fileName = name.toString();
                    if (fileName.endsWith(".jar")) {
                        log.info("[HotReload] File change detected: pluginId={}, kind={}", pluginId, kind);
                        scheduleDebounce(pluginId);
                    }
                }
            }
            key.reset();
        }
    }

    private void registerPluginWatchKey(String pluginId) {
        if (watchKeys.containsKey(pluginId)) return;
        Path pluginDir = pluginsDir.resolve(pluginId);
        if (!Files.isDirectory(pluginDir)) return;
        try {
            WatchKey key = pluginDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            watchKeys.put(pluginId, key);
        } catch (IOException e) {
            log.error("[HotReload] Failed to register watch for plugin {}: {}", pluginId, e.getMessage());
        }
    }

    private void cancelWatchKey(String pluginId) {
        WatchKey key = watchKeys.remove(pluginId);
        if (key != null) {
            key.cancel();
        }
    }

    private void scheduleDebounce(String pluginId) {
        ScheduledFuture<?> existing = debounceTimers.remove(pluginId);
        if (existing != null) {
            existing.cancel(false);
            log.debug("[HotReload] Reset debounce: {}", pluginId);
        }
        log.info("[HotReload] Debounce started (2s): {}", pluginId);
        ScheduledFuture<?> future = scheduler.schedule(() -> executeDebounced(pluginId), 2, TimeUnit.SECONDS);
        debounceTimers.put(pluginId, future);
    }

    private void executeDebounced(String pluginId) {
        debounceTimers.remove(pluginId);
        String reloadId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[HotReload] reloadId={}, pluginId={}, trigger: watch", reloadId, pluginId);

        Path pluginDir = pluginsDir.resolve(pluginId);
        if (!Files.isDirectory(pluginDir)) {
            log.info("[HotReload] Debounce expired, plugin dir removed, unloading: {}", pluginId);
            pluginService.unloadPlugin(pluginId);
            cancelWatchKey(pluginId);
            return;
        }

        boolean hasJar;
        try (var paths = Files.list(pluginDir)) {
            hasJar = paths.anyMatch(p -> {
                String name = p.getFileName().toString();
                return name.startsWith(pluginId + "-") && name.endsWith(".jar");
            });
        } catch (IOException e) {
            log.error("[HotReload] Failed to validate plugin directory: {}", pluginId, e);
            return;
        }

        if (!hasJar) {
            log.info("[HotReload] No valid JAR found for {}: skipping", pluginId);
            return;
        }

        try {
            pluginService.unloadPlugin(pluginId);
            pluginService.installPlugin(pluginId);
            log.info("[HotReload] reloadId={} completed", reloadId);
        } catch (Exception e) {
            log.error("[HotReload] reloadId={} failed: {}", reloadId, e.getMessage(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("[HotReload] WatchService shutting down...");
        running = false;
        scheduler.shutdown();
        watchKeys.values().forEach(WatchKey::cancel);
        watchKeys.clear();
        try {
            watchService.close();
        } catch (IOException e) {
            log.warn("[HotReload] WatchService close error: {}", e.getMessage());
        }
        log.info("[HotReload] WatchService shut down");
    }
}
