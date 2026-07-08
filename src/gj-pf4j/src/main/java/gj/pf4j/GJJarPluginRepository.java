/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import org.pf4j.JarPluginRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GJJarPluginRepository extends JarPluginRepository {

    private static final Logger log = LoggerFactory.getLogger(GJJarPluginRepository.class);

    public GJJarPluginRepository(List<Path> pluginsRoots) {
        super(pluginsRoots);
    }

    @Override
    protected Stream<File> streamFiles(Path directory, FileFilter filter) {
        Stream<File> jarFiles = findLatestJarFilesInRoot(directory);
        return filter != null ? jarFiles.filter(filter::accept) : jarFiles;
    }

    @Override
    public boolean deletePluginPath(Path pluginPath) {
        if (pluginPath == null || !Files.exists(pluginPath)) {
            log.debug("Plugin path is null or does not exist, skipping deletion: {}", pluginPath);
            return false;
        }

        try {
            Files.walkFileTree(pluginPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        log.warn("Failed to delete file: {}", file, e);
                        // Continue trying to delete other files
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    try {
                        Files.delete(dir);
                    } catch (IOException e) {
                        log.warn("Failed to delete directory: {}", dir, e);
                        // Continue trying to delete other directories
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("Successfully deleted plugin directory: {}", pluginPath);
            return true;
        } catch (IOException e) {
            log.error("Unexpected I/O error while deleting plugin directory: {}", pluginPath, e);
            return false;
        }
    }

    // ========================
    // Public API
    // ========================

    /**
     * Scan the plugin root directory, return the latest JAR for each plugin, and detect same-name JAR conflicts.
     */
    public Stream<File> findLatestJarFilesInRoot(Path pluginRoot) {
        if (!Files.isDirectory(pluginRoot)) {
            log.warn("Plugin root is not a directory: {}", pluginRoot);
            return Stream.empty();
        }

        List<File> allLatestJars;
        try (Stream<Path> subDirs = Files.list(pluginRoot)) {
            allLatestJars = subDirs
                    .filter(Files::isDirectory)
                    .flatMap(this::findLatestJarFileInDir)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list plugin root directory: {}", pluginRoot, e);
            return Stream.empty();
        }

        // === Detect same-name JAR conflicts ===
        detectAndLogJarConflicts(allLatestJars, pluginRoot);

        return allLatestJars.stream();
    }

    // ========================
    // Conflict Detection
    // ========================

    private void detectAndLogJarConflicts(List<File> jarFiles, Path pluginRoot) {
        if (jarFiles.isEmpty()) {
            return;
        }

        // Group by file name
        Map<String, List<File>> jarsByName = jarFiles.stream()
                .collect(Collectors.groupingBy(File::getName));

        // Find conflicts (occurring >= 2 times)
        Map<String, List<File>> conflicts = jarsByName.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!conflicts.isEmpty()) {
            log.warn("Detected conflicting JAR files with identical names in plugin root: {}", pluginRoot);
            for (Map.Entry<String, List<File>> entry : conflicts.entrySet()) {
                String jarName = entry.getKey();
                List<String> sourceDirs = entry.getValue().stream()
                        .map(file -> {
                            Path parent = file.toPath().getParent();
                            return parent != null ? pluginRoot.relativize(parent).toString() : file.getAbsolutePath();
                        })
                        .distinct()
                        .collect(Collectors.toList());

                log.warn("Conflict JAR: {} (found in {} plugin directories: {})",
                        jarName, sourceDirs.size(), sourceDirs);
            }
        }
    }

    // ========================
    // Internal Helpers (unchanged except minor tweaks)
    // ========================

    private Stream<File> findLatestJarFileInDir(Path pluginDir) {
        try (Stream<Path> paths = Files.list(pluginDir)) {
            String baseName = removeVersion(pluginDir.getFileName().toString());

            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(".jar"))
                    .map(path -> {
                        String fileName = path.getFileName().toString();
                        JarVersionInfo info = parseJarVersion(fileName, baseName);
                        return info != null ? new JarWithVersion(path.toFile(), info) : null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(
                            jar -> jar.versionInfo.baseName,
                            Collectors.maxBy(Comparator.comparing(jar -> jar.versionInfo.version))
                    ))
                    .values()
                    .stream()
                    .filter(Optional::isPresent)
                    .map(opt -> opt.get().file)
                    .toList()
                    .stream();
        } catch (IOException e) {
            log.error("Failed to read plugins directory: {}", pluginDir, e);
            return Stream.empty();
        }
    }

    private String removeVersion(String name) {
        return name.replaceFirst("-\\d+(?:\\.\\d+)*(?:-[a-zA-Z0-9.-]+)?$", "");
    }

    private JarVersionInfo parseJarVersion(String jarFileName, String expectedBaseName) {
        if (!jarFileName.toLowerCase().endsWith(".jar")) {
            return null;
        }

        String nameWithoutExt = jarFileName.substring(0, jarFileName.length() - 4);
        if (!nameWithoutExt.startsWith(expectedBaseName + "-")) {
            return null;
        }

        String versionPart = nameWithoutExt.substring(expectedBaseName.length() + 1);
        if (versionPart.isEmpty() || !versionPart.matches(".*\\d.*")) {
            return null;
        }

        try {
            Version version = new Version(versionPart);
            return new JarVersionInfo(expectedBaseName, version);
        } catch (Exception e) {
            log.error("Failed to parse version from JAR: {}", jarFileName, e);
            return null;
        }
    }

    // ========================
    // Helper Classes (unchanged)
    // ========================

    private record JarWithVersion(File file, JarVersionInfo versionInfo) {
    }

    private record JarVersionInfo(String baseName, Version version) {
    }

    private static class Version implements Comparable<Version> {
        private final String original;
        private final String release;
        private final String preRelease;

        private static final Pattern VERSION_PATTERN = Pattern.compile(
                "^(\\d+(?:\\.\\d+)*)(?:-([a-zA-Z0-9.-]+))?$"
        );

        public Version(String versionStr) {
            this.original = versionStr.toLowerCase().trim();
            Matcher m = VERSION_PATTERN.matcher(this.original);
            if (m.matches()) {
                this.release = m.group(1);
                this.preRelease = m.group(2);
            } else {
                this.release = "0.0.0";
                this.preRelease = this.original;
            }
        }

        @Override
        public int compareTo(Version other) {
            int releaseCompare = compareRelease(this.release, other.release);
            if (releaseCompare != 0) return releaseCompare;

            if (this.preRelease == null && other.preRelease != null) return 1;
            if (this.preRelease != null && other.preRelease == null) return -1;
            if (this.preRelease == null) return 0;

            return this.preRelease.compareTo(other.preRelease);
        }

        private static int compareRelease(String v1, String v2) {
            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");
            int maxLength = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < maxLength; i++) {
                int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
                int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
                if (num1 != num2) {
                    return Integer.compare(num1, num2);
                }
            }
            return 0;
        }

        @Override
        public String toString() {
            return original;
        }
    }
}
