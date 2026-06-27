/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import org.pf4j.*;

import java.util.concurrent.locks.ReentrantLock;

public class GJPluginService {

    private final GJPluginManager pluginManager;
    private final Object globalLock = new Object();

    public GJPluginService(GJPluginManager pluginManager) {
        this.pluginManager = pluginManager;
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

    public PluginState installPlugin(String pluginId) {
        ReentrantLock lock = pluginManager.getPluginLock(pluginId);
        lock.lock();
        try {
            return pluginManager.installPlugin(pluginId);
        } finally {
            lock.unlock();
        }
    }

    public void disablePlugin(String pluginId) {
        ReentrantLock lock = pluginManager.getPluginLock(pluginId);
        lock.lock();
        try {
            pluginManager.disablePlugin(pluginId);
        } finally {
            lock.unlock();
        }
    }

    public PluginState restartPlugin(String pluginId) {
        ReentrantLock lock = pluginManager.getPluginLock(pluginId);
        lock.lock();
        try {
            return pluginManager.restartPlugin(pluginId);
        } finally {
            lock.unlock();
        }
    }

    public boolean unloadPlugin(String pluginId) {
        ReentrantLock lock = pluginManager.getPluginLock(pluginId);
        lock.lock();
        try {
            boolean succeed = pluginManager.doUnloadPlugin(pluginId);
            if (succeed) {
                pluginManager.removePluginLock(pluginId);
            }
            return succeed;
        } finally {
            lock.unlock();
        }
    }

    public boolean deletePlugin(String pluginId) {
        ReentrantLock lock = pluginManager.getPluginLock(pluginId);
        lock.lock();
        try {
            boolean succeed = pluginManager.deletePlugin(pluginId);
            if (succeed) {
                pluginManager.removePluginLock(pluginId);
            }
            return succeed;
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
