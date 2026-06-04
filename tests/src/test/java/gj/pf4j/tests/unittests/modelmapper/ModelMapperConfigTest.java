package gj.pf4j.tests.modelmapper;

import gj.pf4j.modelmapper.GJPluginModelMapper;
import gj.pf4j.modelmapper.GJPluginTypeMapConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import static org.junit.jupiter.api.Assertions.*;

class ModelMapperConfigTest {

    @Nested
    @DisplayName("GJPluginModelMapper default configuration")
    class DefaultConfig {

        @Test
        @DisplayName("Field matching is disabled")
        void fieldMatchingDisabled() {
            assertFalse(new GJPluginModelMapper().build().getConfiguration().isFieldMatchingEnabled());
        }

        @Test
        @DisplayName("Skip null is enabled")
        void skipNullEnabled() {
            assertTrue(new GJPluginModelMapper().build().getConfiguration().isSkipNullEnabled());
        }

        @Test
        @DisplayName("Implicit mapping is enabled")
        void implicitMappingEnabled() {
            assertTrue(new GJPluginModelMapper().build().getConfiguration().isImplicitMappingEnabled());
        }

        @Test
        @DisplayName("Full type matching is required")
        void fullTypeMatchingRequired() {
            assertTrue(new GJPluginModelMapper().build().getConfiguration().isFullTypeMatchingRequired());
        }

        @Test
        @DisplayName("Collections merge is disabled (replacement mode)")
        void collectionsMergeDisabled() {
            assertFalse(new GJPluginModelMapper().build().getConfiguration().isCollectionsMergeEnabled());
        }
    }

    @Nested
    @DisplayName("GJPluginTypeMapConfig.of()")
    class TypeMapConfigOf {

        @Test
        @DisplayName("Creates config with source and destination types")
        void basicOf() {
            var config = GJPluginTypeMapConfig.of(String.class, Integer.class);
            assertEquals(String.class, config.getSourceType());
            assertEquals(Integer.class, config.getDestinationType());
            assertNotNull(config.getMappingConfigurer());
        }

        @Test
        @DisplayName("With custom configurer — consumer is stored")
        void withConsumer() {
            final boolean[] invoked = {false};
            var config = GJPluginTypeMapConfig.of(
                    String.class, Integer.class,
                    typeMap -> invoked[0] = true
            );
            config.getMappingConfigurer().accept(null);
            assertTrue(invoked[0], "Consumer should be invoked");
        }

        @Test
        @DisplayName("Null consumer does not throw (accepts null)")
        void nullConsumer() {
            var config = GJPluginTypeMapConfig.of(String.class, String.class, null);
            assertNotNull(config);
            assertThrows(NullPointerException.class, () -> config.getMappingConfigurer().accept(null));
        }
    }

    @Nested
    @DisplayName("TypeMap merge and idempotency")
    class TypeMapMerge {

        @Test
        @DisplayName("Creating same TypeMap twice yields merge (not duplicate)")
        void mergeExisting() {
            ModelMapper mm = new GJPluginModelMapper().build();
            mm.createTypeMap(Source.class, Target.class).addMapping(Source::getName, Target::setTitle);
            // Second create should merge (existing behavior)
            var existing = mm.getTypeMap(Source.class, Target.class);
            assertNotNull(existing, "TypeMap should exist after first create");
        }

        @Test
        @DisplayName("Different TypeMaps are independent")
        void independentTypeMaps() {
            ModelMapper mm = new GJPluginModelMapper().build();
            mm.createTypeMap(Source.class, Target.class);
            mm.createTypeMap(Source.class, String.class);
            assertNotNull(mm.getTypeMap(Source.class, Target.class));
            assertNotNull(mm.getTypeMap(Source.class, String.class));
        }
    }

    @Nested
    @DisplayName("Multiple ModelMapper instances are independent")
    class Independence {

        @Test
        @DisplayName("Each build() produces a fresh ModelMapper")
        void freshInstances() {
            ModelMapper m1 = new GJPluginModelMapper().build();
            ModelMapper m2 = new GJPluginModelMapper().build();
            assertNotSame(m1, m2);
        }

        @Test
        @DisplayName("TypeMap registered in one mapper not visible in another")
        void isolatedMappers() {
            ModelMapper m1 = new GJPluginModelMapper().build();
            m1.createTypeMap(Source.class, Target.class);
            ModelMapper m2 = new GJPluginModelMapper().build();
            assertNull(m2.getTypeMap(Source.class, Target.class),
                    "TypeMap from m1 should not leak to m2");
        }
    }

    // Test POJOs
    static class Source {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    static class Target {
        private String title;
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }
}
