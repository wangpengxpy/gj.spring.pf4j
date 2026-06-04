package gj.pf4j.examples.eventbus;

import gj.pf4j.eventbus.GJPluginLocalEventListener;
import gj.pf4j.examples.eventbus.ExampleEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExampleListener implements GJPluginLocalEventListener<ExampleEvent> {

    @Override
    public void HandleEvent(ExampleEvent event) {
        log.info("Received ExampleEvent: id={}, name={}", event.getId(), event.getName());
    }
}
