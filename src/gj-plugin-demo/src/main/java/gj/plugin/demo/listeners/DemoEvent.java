package gj.plugin.demo.listeners;

import gj.pf4j.eventbus.EventName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EventName("demo.event.test")
public class DemoEvent {

    private String message;

    private long timestamp;
}
