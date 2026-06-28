/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * Internal Jackson ObjectMapper utility for the plugin framework.
 */
public final class GJJackson {

    private static final Logger log = LoggerFactory.getLogger(GJJackson.class);

    private GJJackson() {}

    // ── Instance holder (framework-level shared reference) ──────

    static volatile ObjectMapper INSTANCE;

    static void setInstance(ObjectMapper mapper) {
        if (mapper == null) throw new IllegalArgumentException("ObjectMapper cannot be null");
        INSTANCE = mapper;
    }

    // ── ObjectMapper resolution ──────────────────────────────────

    /**
     * Resolve ObjectMapper with three-level fallback:
     * <ol>
     *   <li>ObjectMapper bean from host application context</li>
     *   <li>Build via Jackson2ObjectMapperBuilder bean if present</li>
     *   <li>Create default ObjectMapper (8 features, .NET-aligned)</li>
     * </ol>
     */
    public static ObjectMapper resolveObjectMapper(ApplicationContext mainCtx) {
        // Level 1: Direct ObjectMapper bean lookup
        try {
            ObjectMapper mapper = mainCtx.getBeanProvider(ObjectMapper.class).getIfAvailable();
            if (mapper != null) {
                log.info("Resolved ObjectMapper from host application context: {}",
                        mapper.getClass().getName());
                return mapper;
            }
        } catch (Exception e) {
            log.debug("ObjectMapper bean not found in host context, trying fallback", e);
        }

        // Level 2: Build via Jackson2ObjectMapperBuilder
        try {
            Class<?> builderClass = Class.forName(
                    "org.springframework.http.converter.json.Jackson2ObjectMapperBuilder");
            Object builder = mainCtx.getBeanProvider(builderClass).getIfAvailable();
            if (builder != null) {
                java.lang.reflect.Method buildMethod = builderClass.getMethod("build");
                ObjectMapper mapper = (ObjectMapper) buildMethod.invoke(builder);
                log.info("Resolved ObjectMapper via Jackson2ObjectMapperBuilder from host context");
                return mapper;
            }
        } catch (Exception e) {
            log.debug("Jackson2ObjectMapperBuilder not found in host context, using default", e);
        }

        // Level 3: Create default ObjectMapper
        log.warn("No ObjectMapper bean found in host context. " +
                "Creating default ObjectMapper. Consider adding Spring Boot Jackson " +
                "auto-configuration or defining an ObjectMapper @Bean.");
        return createDefaultObjectMapper();
    }

    /**
     * Create default ObjectMapper with 8 features aligned to .NET defaults.
     * The 8 features ensure cross-platform JSON compatibility out of the box.
     */
    public static ObjectMapper createDefaultObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                // Disable: dates-as-timestamps, unknown-property failures, timezone adjustment
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                // Enable: case-insensitive props/enums, single-value-as-array, comments
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                .enable(JsonParser.Feature.ALLOW_COMMENTS)
                .build();
    }
}
