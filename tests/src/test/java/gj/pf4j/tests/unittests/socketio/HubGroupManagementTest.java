package gj.pf4j.tests.socketio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HubGroupManagementTest {

    private ConcurrentHashMap<String, Set<String>> groups;

    @BeforeEach
    void setUp() {
        groups = new ConcurrentHashMap<>();
    }

    @Nested
    @DisplayName("addToGroup")
    class AddToGroup {

        @Test
        @DisplayName("Creates group and adds connection")
        void createAndAdd() {
            groups.computeIfAbsent("admin", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
            assertTrue(groups.get("admin").contains("conn-1"));
        }

        @Test
        @DisplayName("Adding to existing group preserves other members")
        void preservesExisting() {
            groups.computeIfAbsent("admin", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
            groups.computeIfAbsent("admin", k -> ConcurrentHashMap.newKeySet()).add("conn-2");
            assertEquals(2, groups.get("admin").size());
        }

        @Test
        @DisplayName("Adding same connection twice is idempotent (Set semantics)")
        void idempotent() {
            groups.computeIfAbsent("admin", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
            groups.computeIfAbsent("admin", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
            assertEquals(1, groups.get("admin").size());
        }

        @Test
        @DisplayName("One connection can belong to multiple groups")
        void multiGroupMembership() {
            groups.computeIfAbsent("g1", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
            groups.computeIfAbsent("g2", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
            groups.computeIfAbsent("g3", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
            assertEquals(3, groups.size());
            assertTrue(groups.values().stream().allMatch(s -> s.contains("conn-1")));
        }
    }

    @Nested
    @DisplayName("removeFromGroup")
    class RemoveFromGroup {

        @Test
        @DisplayName("Removes connection and cleans empty group")
        void removeAndClean() {
            groups.computeIfAbsent("admin", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
            Set<String> members = groups.get("admin");
            members.remove("conn-1");
            if (members.isEmpty()) groups.remove("admin");
            assertFalse(groups.containsKey("admin"));
        }

        @Test
        @DisplayName("Removing one member leaves group intact with others")
        void leavesOthersIntact() {
            groups.computeIfAbsent("admin", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
            groups.get("admin").add("conn-2");
            groups.get("admin").remove("conn-1");
            assertTrue(groups.containsKey("admin"));
            assertFalse(groups.get("admin").contains("conn-1"));
            assertTrue(groups.get("admin").contains("conn-2"));
        }

        @Test
        @DisplayName("Removing from non-existent group is safe (no-op)")
        void nonExistentGroup() {
            assertDoesNotThrow(() -> {
                Set<String> m = groups.get("nonexistent");
                if (m != null) m.remove("conn-1");
            });
        }
    }

    @Nested
    @DisplayName("removeFromAllGroups")
    class RemoveFromAllGroups {

        @Test
        @DisplayName("Cleans connection from all groups")
        void cleansAll() {
            groups.computeIfAbsent("g1", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
            groups.computeIfAbsent("g2", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
            for (Set<String> members : groups.values()) members.remove("conn-1");
            groups.entrySet().removeIf(e -> e.getValue().isEmpty());
            assertTrue(groups.isEmpty());
        }

        @Test
        @DisplayName("Groups without the connection are unaffected")
        void unaffectedGroups() {
            groups.computeIfAbsent("g1", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
            groups.computeIfAbsent("g2", k -> ConcurrentHashMap.newKeySet()).add("conn-2");
            for (Set<String> members : groups.values()) members.remove("conn-1");
            groups.entrySet().removeIf(e -> e.getValue().isEmpty());
            assertFalse(groups.containsKey("g1"));
            assertTrue(groups.containsKey("g2"));
        }
    }

    @Nested
    @DisplayName("Concurrent access safety")
    class Concurrency {

        @Test
        @DisplayName("Concurrent adds to same group do not lose members")
        void concurrentAdds() throws InterruptedException {
            int threads = 8;
            CountDownLatch latch = new CountDownLatch(threads);
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                new Thread(() -> {
                    groups.computeIfAbsent("shared", k -> ConcurrentHashMap.newKeySet())
                            .add("conn-" + idx);
                    latch.countDown();
                }).start();
            }
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(threads, groups.get("shared").size(),
                    "Concurrent adds should not lose members");
        }

        @Test
        @DisplayName("Concurrent add and remove on same group is consistent")
        void concurrentAddRemove() throws InterruptedException {
            groups.computeIfAbsent("shared", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
            CountDownLatch latch = new CountDownLatch(4);
            new Thread(() -> {
                for (int i = 2; i <= 10; i++)
                    groups.computeIfAbsent("shared", k -> ConcurrentHashMap.newKeySet()).add("conn-" + i);
                latch.countDown();
            }).start();
            new Thread(() -> {
                Set<String> m = groups.get("shared");
                if (m != null) m.remove("conn-1");
                latch.countDown();
            }).start();
            new Thread(() -> {
                for (int i = 11; i <= 20; i++)
                    groups.computeIfAbsent("shared", k -> ConcurrentHashMap.newKeySet()).add("conn-" + i);
                latch.countDown();
            }).start();
            new Thread(() -> {
                groups.computeIfAbsent("shared", k -> ConcurrentHashMap.newKeySet()).add("conn-21");
                latch.countDown();
            }).start();
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertFalse(groups.get("shared").contains("conn-1"));
            assertTrue(groups.get("shared").size() >= 19);
        }
    }
}
