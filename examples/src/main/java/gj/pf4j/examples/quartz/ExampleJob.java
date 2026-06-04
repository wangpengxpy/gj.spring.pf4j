package gj.pf4j.examples.jobs;

import gj.pf4j.quartzjob.IPluginJob;
import gj.pf4j.quartzjob.annotation.PluginJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@PluginJob(name = "exampleJob", intervalSeconds = 60)
public class ExampleJob implements IPluginJob {

    @Override
    public void execute() {
        log.info("Scheduled job executed at {}", java.time.LocalDateTime.now());
    }
}
