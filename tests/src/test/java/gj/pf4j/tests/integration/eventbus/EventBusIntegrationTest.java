package gj.pf4j.tests.integration.eventbus;

import gj.pf4j.eventbus.EventName;
import gj.pf4j.eventbus.GJPluginLocalEventBus;
import gj.pf4j.tests.integration.IntegrationTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = IntegrationTestConfig.class)
class EventBusIntegrationTest {

    @Autowired
    private GJPluginLocalEventBus eventBus;

    @Test
    @DisplayName("GJPluginLocalEventBus bean is injected by Spring")
    void eventBusInjected() {
        assertNotNull(eventBus, "EventBus should be auto-wired from Spring context");
    }

    @Test
    @DisplayName("Publish event with no matching listeners returns safely")
    void publishNoListenersSafely() {
        assertDoesNotThrow(() -> {
            eventBus.publish(new TestEvent("data"));
            eventBus.publishAsync(new TestEvent("data"));
        });
    }

    @EventName("test.event")
    static class TestEvent {
        private String payload;
        public TestEvent() {}
        public TestEvent(String payload) { this.payload = payload; }
        public String getPayload() { return payload; }
        public void setPayload(String payload) { this.payload = payload; }
    }
}
