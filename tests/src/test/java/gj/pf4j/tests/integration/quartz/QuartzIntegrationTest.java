package gj.pf4j.tests.integration.quartz;

import gj.pf4j.tests.integration.IntegrationTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = IntegrationTestConfig.class)
class QuartzIntegrationTest {

    @Autowired(required = false)
    private Scheduler scheduler;

    @Test
    @DisplayName("Scheduler bean is created by GJQuartzConfig and started")
    void schedulerIsCreatedAndStarted() {
        assertNotNull(scheduler, "Scheduler should be auto-created by GJQuartzConfig");
        try {
            assertTrue(scheduler.isStarted(), "Scheduler should be started");
        } catch (org.quartz.SchedulerException e) {
            fail("Scheduler check failed: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Scheduler can be checked for job existence")
    void schedulerJobCheck() throws Exception {
        assertNotNull(scheduler);
        assertFalse(scheduler.checkExists(
                new org.quartz.JobKey("nonexistent", "test")),
                "Non-existent job should return false");
    }
}
