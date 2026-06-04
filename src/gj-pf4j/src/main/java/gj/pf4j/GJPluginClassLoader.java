/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import org.pf4j.PluginClassLoader;
import org.pf4j.PluginManager;
import org.pf4j.PluginDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class GJPluginClassLoader extends PluginClassLoader {

    private static final Logger log = LoggerFactory.getLogger(GJPluginClassLoader.class);

    // plugin-specific resources
    private static final Set<String> PLUGIN_FIRST_RESOURCES = Set.of(
            "META-INF/extensions.idx"
    );

    private final ClassLoader parentClassLoader;

    public GJPluginClassLoader(final PluginManager pluginManager,
                               final PluginDescriptor pluginDescriptor,
                               final ClassLoader parent) {
        super(pluginManager, pluginDescriptor, parent);
        this.parentClassLoader = parent;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 1. Check if the class is already loaded
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass != null) {
            return loadedClass;
        }
        // 2. Prioritize loading from the main application (parent class loader)
        try {
            return parentClassLoader.loadClass(name);
        } catch (ClassNotFoundException e) {
            log.debug("Class {} not found in parent classloader, trying plugin", name);
        }
        // 3. Try loading from the plugin
        try {
            Class<?> pluginClass = super.loadClass(name, resolve);
            if (pluginClass != null) {
                return pluginClass;
            }
        } catch (ClassNotFoundException e) {
            log.error("Class {} not found in plugin, trying additional paths", name);
        }
        throw new ClassNotFoundException("Class " + name + " not found in parent, plugin paths");
    }

    @Override
    public URL getResource(String name) {
        // // Exclude plugin-specific resources, prioritize loading from the main application
        if (PLUGIN_FIRST_RESOURCES.contains(name)) {
            URL pluginResource = super.getResource(name);
            if (pluginResource != null) return pluginResource;
            return parentClassLoader.getResource(name);
        }
        // 1. Prioritize searching from the main application (parent class loader)
        URL resource = parentClassLoader.getResource(name);
        if (resource != null) {
            return resource;
        }
        // 2. Search from the plugin
        resource = super.getResource(name);
        if (resource != null) {
            return resource;
        }
        return null;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        // Merge resources from all sources
        List<URL> resources = new ArrayList<>();
        // 1. Get resources from the main application
        Enumeration<URL> parentResources = parentClassLoader.getResources(name);
        while (parentResources.hasMoreElements()) {
            resources.add(parentResources.nextElement());
        }
        // 2. Get resources from the plugin
        Enumeration<URL> pluginResources = super.getResources(name);
        while (pluginResources.hasMoreElements()) {
            resources.add(pluginResources.nextElement());
        }
        return Collections.enumeration(resources);
    }

    @Override
    protected URL findResourceFromDependencies(String name) {
        if (!name.endsWith(".class")) {
            return null;
        }
        return super.findResourceFromDependencies(name);
    }

    @Override
    protected Collection<URL> findResourcesFromDependencies(String name) throws IOException {
        if (!name.endsWith(".class")) {
            return Collections.emptyList();
        }
        return super.findResourcesFromDependencies(name);
    }
}