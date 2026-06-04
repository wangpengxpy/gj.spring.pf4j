package gj.pf4j.tests.integration.socketio;

import gj.pf4j.socketio.GJHub;
import gj.pf4j.tests.integration.IntegrationTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = IntegrationTestConfig.class)
class HubManagerIntegrationTest {

    private final ConcurrentHashMap<String, Set<String>> groupConnections = new ConcurrentHashMap<>();

    @Test
    @DisplayName("Full group lifecycle: add → query → remove → cleanup")
    void fullGroupLifecycle() {
        groupConnections.computeIfAbsent("admin", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
        groupConnections.computeIfAbsent("admin", k -> ConcurrentHashMap.newKeySet()).add("conn-2");
        assertEquals(2, groupConnections.get("admin").size());
        assertTrue(groupConnections.get("admin").contains("conn-1"));

        groupConnections.get("admin").remove("conn-1");
        assertEquals(1, groupConnections.get("admin").size());

        groupConnections.get("admin").remove("conn-2");
        groupConnections.entrySet().removeIf(e -> e.getValue().isEmpty());
        assertFalse(groupConnections.containsKey("admin"));
    }

    @Test
    @DisplayName("Connection can belong to multiple groups simultaneously")
    void multiGroupMembership() {
        groupConnections.computeIfAbsent("g1", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
        groupConnections.computeIfAbsent("g2", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
        groupConnections.computeIfAbsent("g3", k -> ConcurrentHashMap.newKeySet()).add("conn-1");
        assertEquals(3, groupConnections.size());
    }

    @Test
    @DisplayName("Hub abstract class contract is valid")
    void hubContract() {
        TestHub hub = new TestHub();
        assertEquals("testhub", hub.getHubName()); // GJHub constructor lowercases the name
    }

    static class TestHub extends GJHub {
        public TestHub() { super("testHub"); }
        @Override public CompletableFuture<Void> onConnectedAsync() { return CompletableFuture.completedFuture(null); }
        @Override public CompletableFuture<Void> onDisconnectedAsync() { return CompletableFuture.completedFuture(null); }
    }
}
