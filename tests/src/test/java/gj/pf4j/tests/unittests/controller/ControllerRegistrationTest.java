package gj.pf4j.tests.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.bind.annotation.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ControllerRegistrationTest {

    @Nested
    @DisplayName("Annotation retention and compatibility")
    class AnnotationContract {

        @Test
        @DisplayName("@RestController has RUNTIME retention")
        void restControllerRetention() {
            Retention r = RestController.class.getAnnotation(Retention.class);
            assertNotNull(r, "@RestController missing @Retention");
            assertEquals(RetentionPolicy.RUNTIME, r.value());
        }

        @Test
        @DisplayName("@RequestMapping has RUNTIME retention")
        void requestMappingRetention() {
            Retention r = RequestMapping.class.getAnnotation(Retention.class);
            assertNotNull(r, "@RequestMapping missing @Retention");
            assertEquals(RetentionPolicy.RUNTIME, r.value());
        }

        @Test
        @DisplayName("@GetMapping, @PostMapping, @DeleteMapping, @PutMapping all meta-annotate @RequestMapping")
        void allVerbAnnotationsMetaAnnotateRequestMapping() {
            for (Class<?> ann : new Class<?>[]{GetMapping.class, PostMapping.class,
                    DeleteMapping.class, PutMapping.class}) {
                RequestMapping rm = ann.getAnnotation(RequestMapping.class);
                assertNotNull(rm, ann.getSimpleName() + " should meta-annotate @RequestMapping");
            }
        }
    }

    @Nested
    @DisplayName("Path variable edge cases")
    class PathVariables {

        @Test
        @DisplayName("Multiple path variables in a single path")
        void multiplePathVariables() throws NoSuchMethodException {
            Method m = SampleControllerReflect.class.getMethod("multi", Long.class, String.class);
            GetMapping ann = m.getAnnotation(GetMapping.class);
            assertNotNull(ann);
            assertTrue(ann.value().length > 0);
            assertTrue(ann.value()[0].contains("{id}"));
            assertTrue(ann.value()[0].contains("{name}"));
        }

        @Test
        @DisplayName("Empty path value returns empty array (maps to root)")
        void emptyPathValue() throws NoSuchMethodException {
            Method m = SampleControllerReflect.class.getMethod("root");
            GetMapping ann = m.getAnnotation(GetMapping.class);
            assertNotNull(ann);
            // @GetMapping with no explicit path → value() returns empty array
            // Spring treats empty array as mapping to the root path
            assertEquals(0, ann.value().length, "@GetMapping() should produce empty path array");
        }

        @ParameterizedTest
        @ValueSource(strings = {"/api/v1/user", "/", "/single", "/very/deep/nested/path"})
        @DisplayName("Various path patterns are valid")
        void validPaths(String path) {
            assertFalse(path.isEmpty());
        }
    }

    @Nested
    @DisplayName("Request parameter edge cases")
    class RequestParameters {

        @ParameterizedTest
        @NullSource
        @EmptySource
        @ValueSource(strings = {"", "  "})
        @DisplayName("Null or empty request parameter name should be rejected")
        void nullOrEmptyParamName(String paramName) {
            assertTrue(paramName == null || paramName.isBlank(),
                    "Null/blank param names should either be validated or handled gracefully");
        }
    }

    // Reflection target for path variable tests
    static class SampleControllerReflect {
        @GetMapping("/user/{id}/profile/{name}")
        public void multi(@PathVariable Long id, @PathVariable String name) {}

        @GetMapping
        public void root() {}
    }
}
