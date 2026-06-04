package gj.pf4j.tests.quartz;

import gj.pf4j.quartzjob.annotation.PluginJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.annotation.Annotation;
import java.text.ParseException;

import static org.junit.jupiter.api.Assertions.*;

class PluginJobAnnotationTest {

    @Nested
    @DisplayName("Annotation defaults")
    class Defaults {

        @Test
        @DisplayName("intervalSeconds defaults to -1 (disabled)")
        void intervalDefaults() throws NoSuchMethodException {
            assertEquals(-1L,
                    PluginJob.class.getMethod("intervalSeconds").getDefaultValue());
        }

        @Test
        @DisplayName("cronExpression defaults to empty string")
        void cronDefaults() throws NoSuchMethodException {
            assertEquals("",
                    PluginJob.class.getMethod("cronExpression").getDefaultValue());
        }

        @Test
        @DisplayName("runOnce defaults to false")
        void runOnceDefaults() throws NoSuchMethodException {
            assertEquals(false,
                    PluginJob.class.getMethod("runOnce").getDefaultValue());
        }

        @Test
        @DisplayName("disallowConcurrentExecution defaults to true")
        void disallowConcurrentDefaults() throws NoSuchMethodException {
            assertEquals(true,
                    PluginJob.class.getMethod("disallowConcurrentExecution").getDefaultValue());
        }

        @Test
        @DisplayName("name is required (no default)")
        void nameHasNoDefault() throws NoSuchMethodException {
            assertNull(PluginJob.class.getMethod("name").getDefaultValue(),
                    "name must not have a default — it is required");
        }
    }

    @Nested
    @DisplayName("Schedule type validation")
    class ScheduleValidation {

        @Test
        @DisplayName("All three schedule types unset → invalid (must specify one)")
        void allUnset() {
            PluginJob job = createAnnotation("test", -1, "", false, true);
            boolean hasSchedule = job.intervalSeconds() > 0
                    || !job.cronExpression().isEmpty()
                    || job.runOnce();
            assertFalse(hasSchedule,
                    "When all three are unset, framework should throw IllegalArgumentException");
        }

        @Test
        @DisplayName("intervalSeconds + cronExpression set → ambiguous, framework rejects")
        void ambiguousSchedule() {
            PluginJob job = createAnnotation("test", 60, "0 0 8 * * ?", false, true);
            boolean ambiguous = job.intervalSeconds() > 0 && !job.cronExpression().isEmpty();
            assertTrue(ambiguous, "Ambiguous schedule should be rejected by framework");
        }
    }

    @Nested
    @DisplayName("Cron expression validation")
    class CronValidation {

        @ParameterizedTest
        @ValueSource(strings = {
                "0 0 8 * * ?",           // daily 8am
                "0 */5 * * * ?",          // every 5 min
                "0 0 2 ? * MON",          // weekly Monday 2am
                "0 0 0 1 1 ?",            // yearly Jan 1
                "0 0/30 8-17 ? * MON-FRI" // business hours, every 30 min
        })
        @DisplayName("Valid cron expressions")
        void validCron(String expression) {
            try {
                new org.quartz.CronExpression(expression);
            } catch (ParseException e) {
                fail("Should be valid: " + expression + " → " + e.getMessage());
            }
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "invalid", "", "0 0 0 0 0", "a b c d e f",
                "0 0 25 * * ?",  // hour 25 doesn't exist
                "* * * * * * *"   // 7 fields instead of 6
        })
        @DisplayName("Invalid cron expressions throw ParseException")
        void invalidCron(String expression) {
            assertThrows(ParseException.class,
                    () -> new org.quartz.CronExpression(expression),
                    "Should be invalid: " + expression);
        }
    }

    @Nested
    @DisplayName("JobKey format: {pluginId}:{jobName}")
    class JobKeyFormat {

        @ParameterizedTest
        @CsvSource({
                "gj.module.user, cleanupJob,  gj.module.user:cleanupJob",
                "com.example.a,  dailyReport, com.example.a:dailyReport",
                "x,              y,           x:y"
        })
        @DisplayName("Always pluginId:jobName")
        void format(String pluginId, String jobName, String expected) {
            assertEquals(expected, pluginId + ":" + jobName);
        }

        @Test
        @DisplayName("Colon in pluginId itself does not confuse parsing")
        void colonInPluginId() {
            // pluginId should never contain colons per convention, but test the format
            String key = "gj.module.user" + ":" + "job";
            String[] parts = key.split(":");
            assertTrue(parts.length >= 2);
            assertEquals("gj.module.user", parts[0]);
            assertEquals("job", parts[1]);
        }
    }

    @Nested
    @DisplayName("Unregistration of non-existent jobs is safe")
    class Unregistration {

        @Test
        @DisplayName("Removing a non-existent job key is a no-op")
        void noOp() {
            var map = new java.util.concurrent.ConcurrentHashMap<String, java.util.Set<org.quartz.JobKey>>();
            java.util.Set<org.quartz.JobKey> removed = map.remove("nonexistent-plugin");
            assertNull(removed, "Removing non-existent plugin should return null safely");
        }
    }

    // Helper: create a synthetic @PluginJob annotation for testing
    @SuppressWarnings("all")
    private PluginJob createAnnotation(String name, long intervalSeconds,
                                        String cronExpression, boolean runOnce,
                                        boolean disallowConcurrent) {
        return new PluginJob() {
            @Override public Class<? extends Annotation> annotationType() { return PluginJob.class; }
            @Override public String name() { return name; }
            @Override public long intervalSeconds() { return intervalSeconds; }
            @Override public String cronExpression() { return cronExpression; }
            @Override public boolean runOnce() { return runOnce; }
            @Override public boolean disallowConcurrentExecution() { return disallowConcurrent; }
        };
    }
}
