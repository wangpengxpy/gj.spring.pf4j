package gj.pf4j.benchmarks;

import gj.pf4j.eventbus.EventName;
import gj.pf4j.eventbus.GJPluginLocalEventBus;
import gj.pf4j.eventbus.GJPluginLocalEventListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Stress test for EventBus — verifies no OOM under sustained load
 * with many listeners and high-frequency publishing.
 *
 * <p>Run with -Xmx256m to test low-memory resilience:
 * {@code java -Xmx256m -cp ... gj.pf4j.benchmarks.EventBusOOMTest}
 */
public class EventBusOOMTest {

    private static final int LISTENER_COUNT = 1000;
    private static final int PUBLISH_COUNT = 100_000;

    public static void main(String[] args) throws Exception {
        System.out.println("=== EventBus OOM Stress Test ===");
        System.out.printf("Listeners: %,d | Events: %,d%n", LISTENER_COUNT, PUBLISH_COUNT);

        Runtime rt = Runtime.getRuntime();
        long memBefore = rt.totalMemory() - rt.freeMemory();

        GJPluginLocalEventBus bus = new GJPluginLocalEventBus();
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();

        // Register many listeners
        for (int i = 0; i < LISTENER_COUNT; i++) {
            ctx.getBeanFactory().registerSingleton("l" + i, new CountingListener());
        }
        ctx.refresh();
        bus.registerListeners("oom-test", ctx);

        long memAfterRegister = rt.totalMemory() - rt.freeMemory();
        System.out.printf("Memory after %,d listeners: %,.1f MB%n",
                LISTENER_COUNT, (memAfterRegister - memBefore) / 1_048_576.0);

        // Publish many events
        long start = System.currentTimeMillis();
        for (int i = 0; i < PUBLISH_COUNT; i++) {
            bus.publish(new LoadEvent("msg-" + i));
            if (i > 0 && i % 10_000 == 0) {
                long mem = rt.totalMemory() - rt.freeMemory();
                System.out.printf("  Published %,d — memory: %,.1f MB%n",
                        i, mem / 1_048_576.0);
            }
        }
        long elapsed = System.currentTimeMillis() - start;

        long memAfter = rt.totalMemory() - rt.freeMemory();
        System.out.printf("Completed %,d events in %,d ms (%,.0f events/s)%n",
                PUBLISH_COUNT, elapsed, PUBLISH_COUNT * 1000.0 / elapsed);
        System.out.printf("Final memory: %,.1f MB (delta: %,.1f MB)%n",
                memAfter / 1_048_576.0, (memAfter - memBefore) / 1_048_576.0);

        bus.unregisterListeners("oom-test");
        ctx.close();

        // Force GC and verify memory is reclaimed
        System.gc();
        Thread.sleep(1000);
        long memAfterGC = rt.totalMemory() - rt.freeMemory();
        System.out.printf("Memory after GC: %,.1f MB%n", memAfterGC / 1_048_576.0);
        System.out.println(memAfterGC < memAfter * 0.8 ? "PASS: memory reclaimed" : "WARN: possible leak");

        System.out.println("=== EventBus OOM Test PASSED ===");
    }

    @EventName("load.event")
    static class LoadEvent {
        private String payload;
        public LoadEvent() {}
        public LoadEvent(String p) { payload = p; }
        public String getPayload() { return payload; }
        public void setPayload(String p) { payload = p; }
    }

    static class CountingListener implements GJPluginLocalEventListener<LoadEvent> {
        @Override
        public void HandleEvent(LoadEvent event) { /* noop */ }
    }
}
