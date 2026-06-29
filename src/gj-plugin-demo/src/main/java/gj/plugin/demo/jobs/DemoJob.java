package gj.plugin.demo.jobs;

import gj.pf4j.eventbus.GJPluginLocalEventBus;
import gj.pf4j.quartzjob.IPluginJob;
import gj.pf4j.quartzjob.annotation.PluginJob;
import gj.plugin.demo.listeners.DemoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@PluginJob(name = "demoJob", intervalSeconds = 60)
public class DemoJob implements IPluginJob {

    private static final Logger log = LoggerFactory.getLogger(DemoJob.class);

    private final GJPluginLocalEventBus eventBus;

    public DemoJob(GJPluginLocalEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void execute() {
        log.info("[DemoJob] Executing scheduled task at {}", System.currentTimeMillis());
        eventBus.publishAsync(new DemoEvent("Hello from DemoJob", System.currentTimeMillis()));
    }
}
