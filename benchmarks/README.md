# gj.spring.pf4j Benchmarks

Performance tests for EventBus and Socket.IO Hub group infrastructure.

## Files

| File | Type | Description |
|------|------|-------------|
| `EventBusBenchmark.java` | JMH | Publish throughput (sync/async/no-listeners) |
| `HubGroupBenchmark.java` | JMH | Group add/remove/query/iterate operations |
| `EventBusOOMTest.java` | Stress | 1000 listeners × 100K events, memory monitoring |
| `HubGroupOOMTest.java` | Stress | 10K groups × 50 conns + concurrent ops, OOM detection |

## Prerequisites

Install `gj-pf4j` to local Maven repo first:

```bash
cd ../src/gj-parent && mvn install -DskipTests
cd ../gj-pf4j && mvn install -DskipTests
```

## Run JMH Benchmarks

```bash
# Build the benchmark uber-jar
mvn package -DskipTests

# Run EventBus benchmark
java -jar target/benchmarks.jar EventBusBenchmark

# Run Hub group benchmark
java -jar target/benchmarks.jar HubGroupBenchmark

# Run all benchmarks
java -jar target/benchmarks.jar
```

## Run OOM Stress Tests

Low memory simulation to detect memory leaks:

```bash
# EventBus stress (1K listeners, 100K events)
mvn exec:java -Dexec.mainClass=gj.pf4j.benchmarks.EventBusOOMTest
JAVA_OPTS="-Xmx256m" mvn exec:java -Dexec.mainClass=gj.pf4j.benchmarks.EventBusOOMTest

# Hub group stress (10K groups, concurrent ops)
mvn exec:java -Dexec.mainClass=gj.pf4j.benchmarks.HubGroupOOMTest
JAVA_OPTS="-Xmx256m" mvn exec:java -Dexec.mainClass=gj.pf4j.benchmarks.HubGroupOOMTest
```

## Expected Results

- **EventBus sync publish**: ~500K-2M ops/s (JMH)
- **Hub group add**: ~5-20M ops/s (JMH)
- **OOM tests**: Memory should remain stable, GC should reclaim memory after cleanup
