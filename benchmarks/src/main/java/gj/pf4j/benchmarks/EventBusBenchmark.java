package gj.pf4j.benchmarks;

import gj.pf4j.eventbus.EventName;
import gj.pf4j.eventbus.GJPluginLocalEventBus;
import gj.pf4j.eventbus.GJPluginLocalEventListener;
import org.openjdk.jmh.annotations.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for GJPluginLocalEventBus publish throughput.
 *
 * <p>Run via: {@code java -jar target/benchmarks.jar EventBusBenchmark}
 *
 * <p>Generates the uber-jar first with: {@code mvn package -pl benchmarks -DskipTests}
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class EventBusBenchmark {

    private GJPluginLocalEventBus eventBus;
    private AnnotationConfigApplicationContext ctx;

    @Setup
    public void setup() {
        eventBus = new GJPluginLocalEventBus();
        ctx = new AnnotationConfigApplicationContext();
        ctx.getBeanFactory().registerSingleton("l", new NoopListener());
        ctx.refresh();
        eventBus.registerListeners("bm", ctx);
    }

    @TearDown
    public void teardown() {
        eventBus.unregisterListeners("bm");
        ctx.close();
    }

    @Benchmark
    public void syncPublish() {
        eventBus.publish(new PingEvent("ping"));
    }

    @Benchmark
    public void asyncPublish() {
        eventBus.publishAsync(new PingEvent("ping"));
    }

    @Benchmark
    public void publishNoListeners() {
        eventBus.publish(new UnknownPing());
    }

    @EventName("ping.event")
    static class PingEvent {
        private String message;
        public PingEvent() {}
        public PingEvent(String m) { message = m; }
        public String getMessage() { return message; }
        public void setMessage(String m) { message = m; }
    }

    @EventName("unknown.ping")
    static class UnknownPing {}

    static class NoopListener implements GJPluginLocalEventListener<PingEvent> {
        @Override
        public void HandleEvent(PingEvent event) { /* noop */ }
    }
}
