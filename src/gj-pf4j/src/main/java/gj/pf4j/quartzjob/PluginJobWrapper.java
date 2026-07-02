/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.quartzjob;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisallowConcurrentExecution
public class PluginJobWrapper implements Job {

    private static final Logger log = LoggerFactory.getLogger(PluginJobWrapper.class);

    static final String JOB_DATA_PLUGIN_ID = "gj.pluginId";
    static final String JOB_DATA_BEAN_NAME = "gj.beanName";

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap dataMap = context.getJobDetail().getJobDataMap();
        String pluginId = dataMap.getString(JOB_DATA_PLUGIN_ID);
        String beanName = dataMap.getString(JOB_DATA_BEAN_NAME);

        if (pluginId == null || beanName == null) {
            throw new JobExecutionException("JobDataMap missing pluginId or beanName");
        }

        var pluginCtx = PluginJobManager.getPluginContext(pluginId);
        if (pluginCtx == null) {
            log.warn("Plugin '{}' context is not active, skipping job '{}'", pluginId, beanName);
            return;
        }

        IPluginJob pluginJob;
        try {
            pluginJob = pluginCtx.getBean(beanName, IPluginJob.class);
        } catch (Exception e) {
            throw new JobExecutionException("Failed to resolve IPluginJob bean '" + beanName
                    + "' in plugin '" + pluginId + "'", e);
        }

        long start = System.currentTimeMillis();
        try {
            pluginJob.execute();
            log.debug("[Plugin: {}] Job '{}' executed in {}ms", pluginId, beanName,
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[Plugin: {}] Job '{}' failed after {}ms", pluginId, beanName,
                    System.currentTimeMillis() - start, e);
            throw new JobExecutionException(e, false);
        }
    }
}
