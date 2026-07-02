/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.migration.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default {@link ScriptScanner} implementation that scans the classpath
 * for SQL scripts matching {@code NN-description.sql}.
 */
public class ClasspathScriptScanner implements ScriptScanner {

    private static final Logger log = LoggerFactory.getLogger(ClasspathScriptScanner.class);

    private static final Pattern DEFAULT_PATTERN = Pattern.compile("^(\\d+)-(.+)\\.sql$");

    private final Pattern filenamePattern;

    public ClasspathScriptScanner() {
        this(DEFAULT_PATTERN);
    }

    /** Allow customization of the filename matching pattern. */
    public ClasspathScriptScanner(Pattern filenamePattern) {
        this.filenamePattern = filenamePattern;
    }

    @Override
    public List<ScriptResource> scan(String locationPattern, ClassLoader classLoader) {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
        List<ScriptResource> scripts = new ArrayList<>();

        try {
            Resource[] resources = resolver.getResources(locationPattern);
            for (Resource res : resources) {
                String filename = res.getFilename();
                if (filename == null) continue;

                Matcher m = filenamePattern.matcher(filename);
                if (!m.matches()) {
                    log.debug("[ScriptScanner] Skipping non-conforming file: {}", filename);
                    continue;
                }

                int order = Integer.parseInt(m.group(1));
                String description = m.group(2);
                String content;
                try (InputStream is = res.getInputStream()) {
                    content = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
                }
                scripts.add(new ScriptResource(filename, order, description, content));
            }
        } catch (Exception e) {
            log.debug("[ScriptScanner] No scripts found at: {}", locationPattern);
            return List.of();
        }

        scripts.sort(Comparator.comparingInt(ScriptResource::order));
        log.info("[ScriptScanner] Found {} script(s): {}",
                scripts.size(),
                scripts.stream().map(ScriptResource::name).toList());
        return scripts;
    }
}
