/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class GJJackson {
    public static final ObjectMapper INSTANCE =
            JsonMapper.builder()
                    // Key: Disable serializing dates as timestamps (use ISO-8601 strings instead)
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    // Key: Register Java 8 time module (supports LocalDateTime, ZonedDateTime, etc.)
                    .addModule(new JavaTimeModule())
                    // Key: Explicitly enable lower camel case
                    .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                    // Key: Enable case-insensitive properties (default is case-sensitive)
                    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                    // Key: Ignore JSON fields that do not exist in the Java class (matching .NET default behavior)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    // Key: Allow comments in JSON
                    .enable(JsonParser.Feature.ALLOW_COMMENTS)
                    .build();
}
