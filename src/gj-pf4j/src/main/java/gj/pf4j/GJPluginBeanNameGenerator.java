/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.lang.NonNull;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.beans.Introspector;

/**
 * Generate a prefixed Bean name for plugins to avoid Bean name conflicts between different plugins.
 * <p>
 * - If the plugin ID is "user", the default Bean name of UserService will be "user.userService"
 * - If @Service("myService") is used, the final name will be "user.myService"
 * - Degrade to the Spring default naming strategy when the plugin ID is "plugin" or empty
 */
public class GJPluginBeanNameGenerator extends AnnotationBeanNameGenerator {

    private static final Logger log = LoggerFactory.getLogger(GJPluginBeanNameGenerator.class);

    private final String pluginPrefix;

    public GJPluginBeanNameGenerator(String pluginId) {
        this.pluginPrefix = StringUtils.hasText(pluginId) && !"plugin".equals(pluginId) ? pluginId + "." : null;
        if (log.isDebugEnabled()) {
            log.debug("Initialized PluginBeanNameGenerator with pluginId='{}', effective prefix='{}'",
                    pluginId, this.pluginPrefix);
        }
    }

    @Override
    @NonNull
    protected String buildDefaultBeanName(@NonNull BeanDefinition definition) {
        if (pluginPrefix == null) {
            String defaultName = super.buildDefaultBeanName(definition);
            if (log.isTraceEnabled()) {
                log.trace("Using default Spring bean name (no prefix): '{}'", defaultName);
            }
            return defaultName;
        }
        String beanClassName = definition.getBeanClassName();
        if (beanClassName == null) {
            String fallbackName = "unnamedBean_" + System.identityHashCode(definition);
            log.warn("BeanDefinition has null beanClassName. Using fallback name: '{}'. Definition: {}",
                    fallbackName, definition);
            return pluginPrefix + fallbackName;
        }
        String shortClassName = ClassUtils.getShortName(beanClassName);
        String baseName = Introspector.decapitalize(shortClassName);
        String pluginId = pluginPrefix.substring(0, pluginPrefix.length() - 1);
        String optimizedBaseName = baseName;
        // If the class name starts with the plugin suffix (case-insensitive), only retain the suffix
        if (StringUtils.hasText(pluginId) &&
                shortClassName.regionMatches(true, 0, pluginId, 0, pluginId.length())) {
            String suffix = shortClassName.substring(pluginId.length());
            if (!suffix.isEmpty()) {
                optimizedBaseName = Introspector.decapitalize(suffix);
            }
        }
        String finalName = pluginPrefix + optimizedBaseName;
        if (log.isDebugEnabled()) {
            if (!optimizedBaseName.equals(baseName)) {
                log.debug("Optimized bean name from '{}' to '{}' for class '{}'",
                        pluginPrefix + baseName, finalName, beanClassName);
            } else {
                log.debug("Generated bean name: '{}' for class '{}'", finalName, beanClassName);
            }
        }
        return finalName;
    }

    @Override
    protected String determineBeanNameFromAnnotation(@NonNull AnnotatedBeanDefinition annotatedDef) {
        String originalName = super.determineBeanNameFromAnnotation(annotatedDef);
        if (originalName == null) {
            if (log.isTraceEnabled()) {
                String className = annotatedDef.getBeanClassName();
                log.trace("No explicit bean name found via annotation for class '{}'. Will use default naming.",
                        className != null ? className : "unknown");
            }
            return null;
        }

        if (pluginPrefix == null) {
            if (log.isTraceEnabled()) {
                log.trace("Using explicit bean name without prefix: '{}'", originalName);
            }
            return originalName;
        }

        String prefixedName = pluginPrefix + originalName;
        if (log.isDebugEnabled()) {
            String className = annotatedDef.getBeanClassName();
            log.debug("Applied plugin prefix to explicit bean name: '{}' → '{}' (class: '{}')",
                    originalName, prefixedName, className != null ? className : "unknown");
        }

        return prefixedName;
    }
}