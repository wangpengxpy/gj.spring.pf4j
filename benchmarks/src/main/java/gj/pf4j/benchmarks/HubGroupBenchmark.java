package gj.pf4j.benchmarks;

import org.openjdk.jmh.annotations.*;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for Hub group management operations.
 * Simulates GJHubManager.groupConnections behavior.
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class HubGroupBenchmark {

    private ConcurrentHashMap<String, Set<String>> groups;

    @Setup
    public void setup() {
        groups = new ConcurrentHashMap<>();
        // Pre-populate 100 groups with 10 connections each
        for (int g = 0; g < 100; g++) {
            Set<String> members = ConcurrentHashMap.newKeySet();
            for (int c = 0; c < 10; c++) {
                members.add("conn-" + g + "-" + c);
            }
            groups.put("group-" + g, members);
        }
    }

    @Benchmark
    public void addToGroup() {
        groups.computeIfAbsent("bm-group", k -> ConcurrentHashMap.newKeySet())
                .add("conn-bm");
    }

    @Benchmark
    public void removeFromGroup() {
        Set<String> m = groups.get("group-50");
        if (m != null) {
            m.remove("conn-50-5");
            m.add("conn-50-5"); // restore for next iteration
        }
    }

    @Benchmark
    public void queryGroupMembership() {
        Set<String> m = groups.get("group-50");
        boolean exists = m != null && m.contains("conn-50-5");
    }

    @Benchmark
    public void groupLookup() {
        groups.get("group-42");
    }

    @Benchmark
    public void iterateAllGroups() {
        for (var entry : groups.entrySet()) {
            entry.getValue().size();
        }
    }
}
