package ${packagePrefix}.${pluginName}.jobs;

import gj.pf4j.quartzjob.IPluginJob;
import gj.pf4j.quartzjob.annotation.PluginJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sample scheduled job — runs every 60 seconds.
 *
 * <p>Remove or replace with your own business logic.
 * See {@link PluginJob} for all scheduling options (cron, interval, runOnce).
 */
@Slf4j
@Component
@PluginJob(name = "${pluginName}SampleJob", intervalSeconds = 60)
public class ${pluginName}SampleJob implements IPluginJob {

    @Override
    public void execute() {
        log.info("[${pluginName}] Scheduled job executing at {}", java.time.LocalDateTime.now());
        // TODO: replace with your business logic
    }
}
