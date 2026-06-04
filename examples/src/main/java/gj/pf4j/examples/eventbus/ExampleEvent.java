package gj.pf4j.examples.eventbus;

import gj.pf4j.eventbus.EventName;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@EventName("example.created")
public class ExampleEvent {
    private Long id;
    private String name;
}
