/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.quartzjob;

import gj.pf4j.quartzjob.annotation.PluginJob;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnBean(Scheduler.class)
public class PluginJobManager {

    private static final Logger log = LoggerFactory.getLogger(PluginJobManager.class);

    /** pluginId to plugin ApplicationContext (used by PluginJobWrapper at runtime to resolve beans). */
    private static final ConcurrentHashMap<String, ApplicationContext> pluginContexts = new ConcurrentHashMap<>();

    /** pluginId to all JobKey sets for that plugin. */
    private final ConcurrentHashMap<String, Set<JobKey>> pluginJobKeys = new ConcurrentHashMap<>();

    private final Scheduler scheduler;

    public PluginJobManager(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    // ==================== public query (used by PluginJobWrapper) ====================

    /**
     * Get the plugin ApplicationContext for runtime bean resolution by PluginJobWrapper.
     */
    static ApplicationContext getPluginContext(String pluginId) {
        return pluginContexts.get(pluginId);
    }

    // ==================== register / unregister ====================

    /**
     * Scan the plugin Spring context for all {@code @PluginJob}-annotated {@link IPluginJob} beans,
     * and register them with the Quartz scheduler.
     */
    public void registerJobs(String pluginId, ApplicationContext pluginCtx) {
        pluginContexts.put(pluginId, pluginCtx);

        Map<String, Object> jobBeans = pluginCtx.getBeansWithAnnotation(PluginJob.class);
        if (jobBeans.isEmpty()) {
            log.debug("[Plugin: {}] No @PluginJob beans found, skipping job registration", pluginId);
            return;
        }

        Set<JobKey> keys = ConcurrentHashMap.newKeySet();

        for (Map.Entry<String, Object> entry : jobBeans.entrySet()) {
            String beanName = entry.getKey();
            Object bean = entry.getValue();

            if (!(bean instanceof IPluginJob)) {
                log.warn("[Plugin: {}] Bean '{}' is annotated with @PluginJob but does not implement IPluginJob, skipping", pluginId, beanName);
                continue;
            }

            Class<?> beanClass = bean.getClass();
            PluginJob annotation = beanClass.getAnnotation(PluginJob.class);

            try {
                JobKey jobKey = scheduleJob(pluginId, beanName, annotation);
                keys.add(jobKey);
                log.info("[Plugin: {}] Registered job: name='{}', class={}", pluginId, annotation.name(), beanClass.getSimpleName());
            } catch (SchedulerException e) {
                log.error("[Plugin: {}] Failed to register job: name='{}', class={}",
                        pluginId, annotation.name(), beanClass.getSimpleName(), e);
            }
        }

        pluginJobKeys.put(pluginId, keys);
        log.info("[Plugin: {}] Job registration completed, {} job(s) registered", pluginId, keys.size());
    }

    /**
     * Remove all scheduled jobs for the given plugin.
     */
    public void unregisterJobs(String pluginId) {
        Set<JobKey> keys = pluginJobKeys.remove(pluginId);
        if (keys == null || keys.isEmpty()) {
            pluginContexts.remove(pluginId);
            return;
        }

        try {
            for (JobKey key : keys) {
                scheduler.pauseJob(key);
            }

            int waitCount = 0;
            while (hasRunningJobs(keys) && waitCount < 30) {
                try {
                    Thread.sleep(1000);
                    waitCount++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            scheduler.deleteJobs(keys.stream().toList());
            log.info("[Plugin: {}] Job unregistration completed, {} job(s) deleted", pluginId, keys.size());
        } catch (SchedulerException e) {
            log.error("[Plugin: {}] Failed to unregister jobs", pluginId, e);
        } finally {
            pluginContexts.remove(pluginId);
        }
    }

    // ==================== internal ====================

    private JobKey scheduleJob(String pluginId, String beanName, PluginJob annotation) throws SchedulerException {
        String jobName = pluginId + ":" + annotation.name();

        JobDataMap dataMap = new JobDataMap();
        dataMap.put(PluginJobWrapper.JOB_DATA_PLUGIN_ID, pluginId);
        dataMap.put(PluginJobWrapper.JOB_DATA_BEAN_NAME, beanName);

        JobBuilder jobBuilder = JobBuilder.newJob(PluginJobWrapper.class)
                .withIdentity(jobName, pluginId)
                .usingJobData(dataMap)
                .storeDurably();

        if (annotation.disallowConcurrentExecution()) {
            jobBuilder = jobBuilder.withIdentity(jobName, pluginId);
        }

        JobDetail jobDetail = jobBuilder.build();
        Trigger trigger = buildTrigger(annotation, jobName, pluginId);

        scheduler.scheduleJob(jobDetail, trigger);
        return jobDetail.getKey();
    }

    @SuppressWarnings("deprecation")
    private Trigger buildTrigger(PluginJob annotation, String jobName, String pluginId) {
        String triggerName = jobName + "_trigger";

        if (annotation.runOnce()) {
            return TriggerBuilder.newTrigger()
                    .withIdentity(triggerName, pluginId)
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withRepeatCount(0)
                            .withMisfireHandlingInstructionFireNow())
                    .build();
        }

        if (!annotation.cronExpression().isEmpty()) {
            if (!CronExpression.isValidExpression(annotation.cronExpression())) {
                throw new IllegalArgumentException(
                        "Invalid cron expression '" + annotation.cronExpression() + "' for job '" + annotation.name() + "'");
            }
            return TriggerBuilder.newTrigger()
                    .withIdentity(triggerName, pluginId)
                    .withSchedule(CronScheduleBuilder.cronSchedule(annotation.cronExpression())
                            .withMisfireHandlingInstructionFireAndProceed())
                    .build();
        }

        if (annotation.intervalSeconds() > 0) {
            return TriggerBuilder.newTrigger()
                    .withIdentity(triggerName, pluginId)
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInSeconds((int) annotation.intervalSeconds())
                            .repeatForever()
                            .withMisfireHandlingInstructionFireNow())
                    .build();
        }

        throw new IllegalArgumentException(
                "Job '" + annotation.name() + "': must specify intervalSeconds, cronExpression, or runOnce");
    }

    private boolean hasRunningJobs(Set<JobKey> keys) throws SchedulerException {
        for (JobExecutionContext ctx : scheduler.getCurrentlyExecutingJobs()) {
            if (keys.contains(ctx.getJobDetail().getKey())) {
                return true;
            }
        }
        return false;
    }
}
