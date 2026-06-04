/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.quartzjob;

import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;

@Configuration
public class GJQuartzConfig {

    private static final Logger log = LoggerFactory.getLogger(GJQuartzConfig.class);

    private Scheduler scheduler;

    @Bean
    @ConditionalOnMissingBean(Scheduler.class)
    public Scheduler scheduler() throws SchedulerException {
        log.info("Creating default Quartz Scheduler (in-memory)");
        scheduler = new StdSchedulerFactory().getScheduler();
        scheduler.start();
        return scheduler;
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            try {
                scheduler.shutdown(true);
            } catch (SchedulerException e) {
                log.error("Failed to shutdown Quartz Scheduler", e);
            }
        }
    }
}
