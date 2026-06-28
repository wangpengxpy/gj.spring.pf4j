/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class GJPluginUtils {

    private static final Logger log = LoggerFactory.getLogger(GJPluginUtils.class);

    /**
     * Validate plugin directory structure
     *
     * @param pluginsDir Plugin root directory path
     * @throws IllegalArgumentException if the directory does not exist or subdirectory names are duplicated
     */
    public static void validatePluginDirectory(Path pluginsDir) {
        if (!Files.exists(pluginsDir)) {
            try {
                Files.createDirectories(pluginsDir);
                log.info("Plugin directory does not exist, auto-created: {}", pluginsDir.toAbsolutePath());
            } catch (IOException e) {
                log.info("Failed to create plugin directory: {}", pluginsDir.toAbsolutePath());
                throw new RuntimeException("Failed to create plugin directory", e);
            }
        }

        if (!Files.isDirectory(pluginsDir)) {
            throw new IllegalArgumentException("Specified path is not a directory: " + pluginsDir.toAbsolutePath());
        }

        // 2. Check for duplicate first-level subdirectory names
        checkDuplicateSubdirectoriesIgnoreCase(pluginsDir);
    }

    /**
     * Check for duplicate first-level subdirectory names under the plugin directory (case-insensitive)
     *
     * @param pluginsDir Plugin root directory path
     * @throws IllegalArgumentException if duplicate subdirectory names are found (case-insensitive)
     */
    private static void checkDuplicateSubdirectoriesIgnoreCase(Path pluginsDir) {
        Set<String> directoryNamesLower = new HashSet<>(); // Store lowercase names for deduplication
        Set<String> originalNames = new TreeSet<>();       // Store original names for error message display
        Set<String> duplicateOriginalNames = new TreeSet<>(); // Store duplicate original names

        try {
            // Only traverse first-level subdirectories (non-recursive)
            try (var stream = Files.list(pluginsDir)) {
                stream
                        .filter(Files::isDirectory)  // Only process directories
                        .map(Path::getFileName)      // Get directory name Path object
                        .forEach(dirPath -> {
                            String originalName = dirPath.toString();
                            String lowerName = originalName.toLowerCase();

                            // If lowercase name already exists, there is a duplicate
                            if (!directoryNamesLower.add(lowerName)) {
                                duplicateOriginalNames.add(originalName);
                                // Find existing original name (for error message)
                                for (String existing : originalNames) {
                                    if (existing.equalsIgnoreCase(originalName)) {
                                        duplicateOriginalNames.add(existing);
                                    }
                                }
                            }
                            originalNames.add(originalName);
                        });
            }

            if (!duplicateOriginalNames.isEmpty()) {
                throw new IllegalArgumentException(
                        String.format("Duplicate subdirectory names found (case-insensitive) in plugin directory: %s (directory path: %s)",
                                duplicateOriginalNames,
                                pluginsDir.toAbsolutePath())
                );
            }

        } catch (IOException e) {
            throw new RuntimeException("Error reading plugin directory: " + pluginsDir.toAbsolutePath(), e);
        }
    }
}
