/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.quartzjob;

import gj.pf4j.migration.DbType;
import gj.pf4j.migration.script.ScriptRunner;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.scheduling.quartz.LocalDataSourceJobStore;
import org.quartz.impl.StdSchedulerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

@Configuration
public class GJQuartzConfig {

    private static final Logger log = LoggerFactory.getLogger(GJQuartzConfig.class);

    private Scheduler scheduler;

    @Primary
    @Bean
    @ConditionalOnMissingBean(Scheduler.class)
    public Scheduler scheduler(GJQuartzProperties properties,
                               ScriptRunner scriptRunner) throws Exception {

        if (properties.getMode() == GJQuartzProperties.Mode.CLUSTERED) {
            return createClusteredScheduler(properties, scriptRunner);
        }
        return createStandaloneScheduler();
    }

    // -- Standalone ----------------------------------------------------

    private Scheduler createStandaloneScheduler() throws SchedulerException {
        log.info("Creating standalone Quartz Scheduler (in-memory)");
        Scheduler scheduler = new StdSchedulerFactory().getScheduler();
        scheduler.start();
        this.scheduler = scheduler;
        return scheduler;
    }

    // -- Clustered -----------------------------------------------------

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean
    public SpringBeanJobFactory springBeanJobFactory() {
        return new SpringBeanJobFactory();
    }

    private Scheduler createClusteredScheduler(GJQuartzProperties properties,
                                                ScriptRunner scriptRunner) throws Exception {
        log.info("Creating clustered Quartz Scheduler (JDBC job store)");

        // Step 1: Ensure Quartz tables exist
        scriptRunner.runFromFramework();

        // Step 2: Build SchedulerFactoryBean
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setDataSource(scriptRunner.getDataSource());
        factory.setOverwriteExistingJobs(false);
        factory.setJobFactory(new SpringBeanJobFactory());

        factory.setQuartzProperties(buildQuartzProperties(properties, scriptRunner.getDataSource()));
        factory.afterPropertiesSet();
        factory.start();

        scheduler = factory.getScheduler();
        return scheduler;
    }

    private Properties buildQuartzProperties(GJQuartzProperties properties, DataSource dataSource) {
        DbType dbType = detectDbType(dataSource);
        Properties props = new Properties();
        props.setProperty("org.quartz.jobStore.class",
                LocalDataSourceJobStore.class.getName());
        props.setProperty("org.quartz.jobStore.isClustered", "true");
        props.setProperty("org.quartz.jobStore.driverDelegateClass",
                resolveDriverDelegate(dbType));
        props.setProperty("org.quartz.scheduler.instanceId", "AUTO");
        props.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        props.setProperty("org.quartz.scheduler.instanceName", "gjScheduler");
        return props;
    }

    // -- Driver delegate resolution ------------------------------------

    private String resolveDriverDelegate(DbType dbType) {
        return switch (dbType) {
            case MySQL      -> "org.quartz.impl.jdbcjobstore.StdJDBCDelegate";
            case PostgreSQL -> "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate";
            case Oracle     -> "org.quartz.impl.jdbcjobstore.OracleDelegate";
            case GaussDB    -> "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate";
            case KingbaseES -> "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate";
            case DM         -> "org.quartz.impl.jdbcjobstore.OracleDelegate";
            case SQLite     -> "org.quartz.impl.jdbcjobstore.StdJDBCDelegate";
        };
    }

    private DbType detectDbType(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            return DbType.fromConnection(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to detect database type", e);
        }
    }

    // -- Shutdown ------------------------------------------------------

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
