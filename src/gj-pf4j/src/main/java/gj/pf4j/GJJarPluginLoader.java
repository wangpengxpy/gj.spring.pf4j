/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import org.pf4j.JarPluginLoader;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GJJarPluginLoader extends JarPluginLoader {

    private static final Logger log = LoggerFactory.getLogger(GJJarPluginLoader.class);

    private static final String LIB_DIR = "lib";

    public GJJarPluginLoader(PluginManager pluginManager) {
        super(pluginManager);
    }

    @Override
    public boolean isApplicable(Path pluginPath) {
        return Files.exists(pluginPath) && FileUtils.isJarFile(pluginPath);
    }

    @Override
    public ClassLoader loadPlugin(Path pluginPath, PluginDescriptor pluginDescriptor) {
        GJPluginClassLoader pluginClassLoader = new GJPluginClassLoader(this.pluginManager, pluginDescriptor, this.getClass().getClassLoader());
        pluginClassLoader.addFile(pluginPath.toFile());
        loadDependencyJars(pluginPath, pluginClassLoader);
        return pluginClassLoader;
    }

    void loadDependencyJars(Path pluginPath, GJPluginClassLoader pluginClassLoader) {
        // Directory where the plugin JAR is located
        Path pluginDir = pluginPath.getParent();
        if (pluginDir == null) {
            log.warn("Plugin path has no parent directory: {}", pluginPath);
            return;
        }
        Path libDir = pluginDir.resolve(LIB_DIR);
        // 1. Skip loading standalone dependencies if LIB_DIR does not exist (no dependencies available)
        if (!Files.exists(libDir)) {
            return;
        }
        // 2. Read dependencies declared in Class-Path from MANIFEST.MF
        List<Path> declaredPaths = getDeclaredClassPathJars(pluginPath);
        if (declaredPaths.isEmpty()) {
            log.debug("No Class-Path found in MANIFEST.MF");
            return;
        }
        // 3. Extract all JAR filenames under lib/ referenced in Class-Path (normalized to filenames)
        Set<String> declaredJarNames = getDeclaredJarNames(declaredPaths);
        if (declaredJarNames.isEmpty()) {
            return;
        }
        Set<String> loadedJarNames = new HashSet<>();
        try (Stream<Path> stream = Files.list(libDir)) {
            List<Path> libJars = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .toList();

            for (Path jarPath : libJars) {
                String jarName = jarPath.getFileName().toString();
                // Load only JARs declared in Class-Path
                if (!declaredJarNames.contains(jarName)) {
                    log.warn("Ignored undeclared JAR in lib/: {}", jarName);
                    continue;
                }
                // Avoid duplicate loading
                if (!loadedJarNames.add(jarName)) {
                    log.warn("Duplicate JAR in lib/: {}", jarName);
                    continue;
                }
                pluginClassLoader.addFile(jarPath.toFile());
                log.debug("Loaded dependency JAR: {}", jarName);
            }
        } catch (IOException e) {
            log.error("Failed to scan lib directory: {}", libDir, e);
        }
    }

    private Set<String> getDeclaredJarNames(List<Path> declaredPaths) {
        Set<String> declaredJarNames = declaredPaths.stream()
                .filter(path -> {
                    try {
                        return path.startsWith(LIB_DIR) ||
                                path.getFileName().toString().equals(path.toString());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .map(path -> {
                    // Resolve relative paths (e.g. "lib/xxx.jar")
                    return path.getFileName().toString();
                })
                .collect(Collectors.toSet());

        if (declaredJarNames.isEmpty()) {
            log.debug("No lib JARs declared in Class-Path");
        }
        return declaredJarNames;
    }

    private List<Path> getDeclaredClassPathJars(Path pluginJarPath) {
        try (JarFile jarFile = new JarFile(pluginJarPath.toFile())) {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                return Collections.emptyList();
            }
            Attributes mainAttrs = manifest.getMainAttributes();
            String classPath = mainAttrs.getValue(Attributes.Name.CLASS_PATH);
            if (classPath == null || classPath.trim().isEmpty()) {
                return Collections.emptyList();
            }
            return Arrays.stream(classPath.trim().split("\\s+"))
                    .filter(s -> !s.isEmpty())
                    .map(Paths::get)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read Class-Path from plugin manifest: " + pluginJarPath, e);
        }
    }
}
