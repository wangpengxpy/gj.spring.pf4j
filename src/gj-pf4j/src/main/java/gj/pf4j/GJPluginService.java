/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import org.pf4j.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class GJPluginService {

    private final GJPluginManager pluginManager;
    private final Object globalLock = new Object();
    private final Map<String, ReentrantLock> pluginLocks =
            new ConcurrentHashMap<>();

    public GJPluginService(GJPluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    private ReentrantLock getPluginLock(String pluginId) {
        return pluginLocks.computeIfAbsent(pluginId, k -> new ReentrantLock());
    }

    public void loadAndStartAllPlugins() {
        synchronized (globalLock) {
            pluginManager.loadPlugins();
            pluginManager.startPlugins();
        }
    }

    public void restartPlugins() {
        synchronized (globalLock) {
            pluginManager.restartPlugins();
        }
    }

    public PluginState startPlugin(String pluginId) {
        ReentrantLock lock = getPluginLock(pluginId);
        lock.lock();
        try {
            return pluginManager.startPlugin(pluginId);
        } finally {
            lock.unlock();
        }
    }

    public PluginState stopPlugin(String pluginId) {
        ReentrantLock lock = getPluginLock(pluginId);
        lock.lock();
        try {
            PluginState pluginState = pluginManager.stopPlugin(pluginId);
            pluginLocks.remove(pluginId);
            return pluginState;
        } finally {
            lock.unlock();
        }
    }

    public boolean deletePlugin(String pluginId) {
        ReentrantLock lock = getPluginLock(pluginId);
        lock.lock();
        try {
            return pluginManager.deletePlugin(pluginId);
        } finally {
            lock.unlock();
        }
    }

    public PluginState restartPlugin(String pluginId) {
        ReentrantLock lock = getPluginLock(pluginId);
        lock.lock();
        try {
            return pluginManager.restartPlugin(pluginId);
        } finally {
            lock.unlock();
        }
    }

    public boolean unloadPlugin(String pluginId) {
        ReentrantLock lock = getPluginLock(pluginId);
        lock.lock();
        try {
            boolean succeed = pluginManager.unloadPlugin(pluginId);
            if (succeed) {
                pluginLocks.remove(pluginId);
            }
            return succeed;
        } finally {
            lock.unlock();
        }
    }

    public PluginState reloadPlugin(String pluginId) {
        ReentrantLock lock = getPluginLock(pluginId);
        lock.lock();
        try {
            return pluginManager.reloadPlugin(pluginId);
        } finally {
            lock.unlock();
        }
    }

    public void reloadAll() {
        synchronized (globalLock) {
            pluginManager.reloadPlugins(false);
        }
    }
}