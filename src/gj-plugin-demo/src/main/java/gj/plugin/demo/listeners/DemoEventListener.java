package gj.plugin.demo.listeners;

import gj.pf4j.eventbus.GJPluginLocalEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DemoEventListener implements GJPluginLocalEventListener<DemoEvent> {

    private static final Logger log = LoggerFactory.getLogger(DemoEventListener.class);

    @Override
    public void HandleEvent(DemoEvent event) {
        log.info("[DemoEventListener] Received event: message='{}', timestamp={}",
                event.getMessage(), event.getTimestamp());
    }
}
