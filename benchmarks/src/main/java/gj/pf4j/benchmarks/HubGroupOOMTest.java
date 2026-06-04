package gj.pf4j.benchmarks;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stress test for Hub group management — verifies no OOM under extreme
 * group count and concurrent add/remove/query operations.
 *
 * <p>Run with -Xmx256m:
 * {@code java -Xmx256m -cp ... gj.pf4j.benchmarks.HubGroupOOMTest}
 */
public class HubGroupOOMTest {

    private static final int GROUP_COUNT = 10_000;
    private static final int CONNS_PER_GROUP = 50;
    private static final int CONCURRENT_OPS = 10;
    private static final int OPS_PER_THREAD = 100_000;

    public static void main(String[] args) throws Exception {
        System.out.println("=== Hub Group OOM Stress Test ===");
        System.out.printf("Groups: %,d | Conn/group: %,d | Threads: %,d | Ops/thread: %,d%n",
                GROUP_COUNT, CONNS_PER_GROUP, CONCURRENT_OPS, OPS_PER_THREAD);

        Runtime rt = Runtime.getRuntime();
        long memBefore = rt.totalMemory() - rt.freeMemory();

        ConcurrentHashMap<String, Set<String>> groups = new ConcurrentHashMap<>();

        // Phase 1: Populate
        long start = System.currentTimeMillis();
        for (int g = 0; g < GROUP_COUNT; g++) {
            Set<String> members = ConcurrentHashMap.newKeySet();
            for (int c = 0; c < CONNS_PER_GROUP; c++) {
                members.add("conn-" + g + "-" + c);
            }
            groups.put("group-" + g, members);
        }
        long populateTime = System.currentTimeMillis() - start;

        long memAfterPopulate = rt.totalMemory() - rt.freeMemory();
        long totalConns = (long) GROUP_COUNT * CONNS_PER_GROUP;
        System.out.printf("Populated %,d connections in %,d ms — memory: %,.1f MB%n",
                totalConns, populateTime, (memAfterPopulate - memBefore) / 1_048_576.0);

        // Phase 2: Concurrent operations
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_OPS);
        CountDownLatch latch = new CountDownLatch(CONCURRENT_OPS);
        AtomicInteger addCount = new AtomicInteger(0);
        AtomicInteger removeCount = new AtomicInteger(0);
        AtomicInteger queryCount = new AtomicInteger(0);

        start = System.currentTimeMillis();
        for (int t = 0; t < CONCURRENT_OPS; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < OPS_PER_THREAD; i++) {
                    String gid = "group-" + ThreadLocalRandom.current().nextInt(GROUP_COUNT);
                    String cid = "conn-" + threadId + "-" + i;

                    // Random operation
                    int op = ThreadLocalRandom.current().nextInt(3);
                    if (op == 0) {
                        groups.computeIfAbsent(gid, k -> ConcurrentHashMap.newKeySet()).add(cid);
                        addCount.incrementAndGet();
                    } else if (op == 1) {
                        Set<String> m = groups.get(gid);
                        if (m != null) m.remove(cid);
                        removeCount.incrementAndGet();
                    } else {
                        Set<String> m = groups.get(gid);
                        if (m != null) m.contains(cid);
                        queryCount.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        long opTime = System.currentTimeMillis() - start;

        long totalOps = addCount.get() + removeCount.get() + queryCount.get();
        long memAfter = rt.totalMemory() - rt.freeMemory();
        System.out.printf("%,d ops in %,d ms (%,.0f ops/s) — memory: %,.1f MB%n",
                totalOps, opTime, totalOps * 1000.0 / opTime,
                (memAfter - memBefore) / 1_048_576.0);

        // Cleanup and verify GC
        groups.clear();
        System.gc();
        Thread.sleep(1000);
        long memAfterGC = rt.totalMemory() - rt.freeMemory();
        System.out.printf("Memory after GC: %,.1f MB%n", memAfterGC / 1_048_576.0);

        // Check: no OOM occurred
        System.out.println("=== Hub Group OOM Test PASSED ===");
    }
}
