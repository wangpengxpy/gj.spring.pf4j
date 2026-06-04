package gj.pf4j.tests.mybatis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MybatisPackageConventionTest {

    private static String normalizeAndDao(String pluginId) {
        if (pluginId == null || pluginId.isBlank())
            throw new IllegalArgumentException("Plugin ID must not be null or empty");
        return pluginId.replace('-', '.') + ".dao";
    }

    @Nested
    @DisplayName("DAO package convention: {pluginId}.dao")
    class Convention {

        @ParameterizedTest
        @CsvSource({
                "gj.module.user,     gj.module.user.dao",
                "com.example.plugin, com.example.plugin.dao",
                "a,                  a.dao",
                "a.b.c.d.e,         a.b.c.d.e.dao"
        })
        @DisplayName("pluginId + '.dao'")
        void happyPath(String pluginId, String expected) {
            assertEquals(expected, normalizeAndDao(pluginId));
        }
    }

    @Nested
    @DisplayName("Hyphen → dot replacement")
    class HyphenReplacement {

        @ParameterizedTest
        @CsvSource({
                "gj-module-user, gj.module.user.dao",
                "my-app,         my.app.dao",
                "x-y-z,          x.y.z.dao"
        })
        @DisplayName("Single and multiple hyphens")
        void normal(String pluginId, String expected) {
            assertEquals(expected, normalizeAndDao(pluginId));
        }

        @Test
        @DisplayName("Leading hyphen → leading dot")
        void leadingHyphen() {
            assertEquals(".test.dao", normalizeAndDao("-test"));
        }

        @Test
        @DisplayName("Trailing hyphen → dot before .dao")
        void trailingHyphen() {
            assertEquals("test..dao", normalizeAndDao("test-"));
        }

        @Test
        @DisplayName("Consecutive hyphens → consecutive dots")
        void consecutiveHyphens() {
            assertEquals("a..b.dao", normalizeAndDao("a--b"));
        }
    }

    @Nested
    @DisplayName("Null / blank pluginId — fail fast")
    class Validation {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {" ", "  ", "\t"})
        @DisplayName("Throws IllegalArgumentException")
        void shouldThrow(String pluginId) {
            assertThrows(IllegalArgumentException.class, () -> normalizeAndDao(pluginId));
        }
    }

    @Nested
    @DisplayName("DAO package isolation between plugins")
    class Isolation {

        @Test
        @DisplayName("Different pluginIds → different DAO packages")
        void different() {
            assertNotEquals(normalizeAndDao("gj.module.user"), normalizeAndDao("gj.module.order"));
        }

        @Test
        @DisplayName("pluginIds that normalize to same package are treated as same plugin")
        void equivalentIdsCollapse() {
            assertEquals(normalizeAndDao("x.y.z"), normalizeAndDao("x-y-z"),
                    "x.y.z and x-y-z both normalize to x.y.z.dao — same plugin convention");
        }

        @Test
        @DisplayName("No collision across truly distinct pluginIds")
        void noCollision() {
            Set<String> set = new HashSet<>();
            for (String pid : new String[]{"gj.module.user", "gj.module.order", "com.a", "io.github.test"})
                assertTrue(set.add(normalizeAndDao(pid)), "Collision: " + pid);
        }
    }
}
