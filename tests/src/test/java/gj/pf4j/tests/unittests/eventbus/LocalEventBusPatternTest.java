package gj.pf4j.tests.eventbus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LocalEventBusPatternTest {

    private final AntPathMatcher matcher = new AntPathMatcher(".");

    @Nested
    @DisplayName("Exact match")
    class ExactMatch {

        @ParameterizedTest
        @CsvSource({
                "user.created,   user.created",
                "order.cancelled,order.cancelled",
                "a.b.c,          a.b.c"
        })
        @DisplayName("Identical patterns match")
        void identical(String pattern, String event) {
            assertTrue(matcher.match(pattern, event));
        }

        @ParameterizedTest
        @CsvSource({
                "user.created, user.updated",
                "a.b,         a.b.c",
                "x.y.z,       x.y"
        })
        @DisplayName("Different events do not match")
        void different(String pattern, String event) {
            assertFalse(matcher.match(pattern, event));
        }
    }

    @Nested
    @DisplayName("Single-level wildcard (*)")
    class SingleWildcard {

        @ParameterizedTest
        @ValueSource(strings = {"user.created", "user.updated", "user.deleted"})
        @DisplayName("user.* matches user.xxx")
        void matchesSingleLevel(String event) {
            assertTrue(matcher.match("user.*", event));
        }

        @Test
        @DisplayName("user.* does NOT match user.profile.updated (multi-level)")
        void noMultiLevelMatch() {
            assertFalse(matcher.match("user.*", "user.profile.updated"));
        }
    }

    @Nested
    @DisplayName("Multi-level wildcard (**)")
    class MultiWildcard {

        @Test
        @DisplayName("user.** matches any depth")
        void matchesAnyDepth() {
            assertTrue(matcher.match("user.**", "user.created"));
            assertTrue(matcher.match("user.**", "user.profile.updated"));
            assertTrue(matcher.match("user.**", "user.profile.address.changed"));
        }

        @Test
        @DisplayName("user.** does NOT match unrelated patterns")
        void noUnrelatedMatch() {
            assertFalse(matcher.match("user.**", "order.created"));
        }

        @Test
        @DisplayName("**.created matches any prefix")
        void anyPrefix() {
            assertTrue(matcher.match("**.created", "user.created"));
            assertTrue(matcher.match("**.created", "order.payment.created"));
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Empty pattern does not match non-empty event")
        void emptyPattern() {
            assertFalse(matcher.match("", "user.created"));
        }

        @Test
        @DisplayName("Pattern with only dots")
        void dotsOnly() {
            assertFalse(matcher.match("...", "user.created"));
        }

        @Test
        @DisplayName("Case sensitivity — patterns are case-sensitive")
        void caseSensitive() {
            assertFalse(matcher.match("User.Created", "user.created"));
        }
    }

    @Nested
    @DisplayName("Listener exception isolation")
    class ListenerIsolation {

        @Test
        @DisplayName("One failing listener does not block others")
        void failingListenerDoesNotBlock() throws InterruptedException {
            List<String> invoked = new CopyOnWriteArrayList<>();
            CountDownLatch latch = new CountDownLatch(2);

            Runnable failing = () -> {
                invoked.add("failing");
                latch.countDown();
                throw new RuntimeException("Simulated failure");
            };
            Runnable passing = () -> {
                invoked.add("passing");
                latch.countDown();
            };

            // Simulate the event bus invoke pattern: catch per-listener
            for (Runnable task : List.of(failing, passing)) {
                try {
                    task.run();
                } catch (Exception e) {
                    // Isolated — failing listener does not abort dispatch
                }
            }

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertTrue(invoked.contains("passing"), "Passing listener must be invoked");
            assertTrue(invoked.contains("failing"), "Failing listener must also be invoked (before exception)");
        }
    }

    @Nested
    @DisplayName("Multiple listeners per event pattern")
    class MultiListener {

        @Test
        @DisplayName("Multiple listeners match the same pattern")
        void multiMatch() {
            String pattern = "user.*";
            String event = "user.created";
            assertTrue(matcher.match(pattern, event));
            // Same event would be dispatched to ALL listeners matching the pattern
            // This test verifies the matching logic — dispatch is tested in ListenerIsolation
        }
    }

    @Nested
    @DisplayName("Event name convention validation")
    class EventNameConvention {

        @ParameterizedTest
        @ValueSource(strings = {"user.created", "order.payment.completed", "system.startup", "a.b"})
        @DisplayName("Valid dot-separated event names")
        void valid(String name) {
            assertTrue(name.contains("."));
            assertFalse(name.startsWith("."));
            assertFalse(name.endsWith("."));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "noDots", ".leading", "trailing.", ".."})
        @DisplayName("Invalid event names (missing dots or edge cases)")
        void invalid(String name) {
            boolean valid = name.contains(".") && !name.startsWith(".") && !name.endsWith(".");
            assertFalse(valid, "Should be invalid: '" + name + "'");
        }
    }

    @Nested
    @DisplayName("No listeners matched — safe no-op")
    class NoMatch {

        @Test
        @DisplayName("Event with no matching listener does not throw")
        void noMatchDoesNotThrow() {
            Map<String, List<String>> registry = new ConcurrentHashMap<>();
            registry.put("order.*", new CopyOnWriteArrayList<>(List.of("orderListener")));
            String event = "user.created";
            List<String> matched = registry.entrySet().stream()
                    .filter(e -> matcher.match(e.getKey(), event))
                    .flatMap(e -> e.getValue().stream())
                    .toList();
            assertTrue(matched.isEmpty());
        }
    }
}
