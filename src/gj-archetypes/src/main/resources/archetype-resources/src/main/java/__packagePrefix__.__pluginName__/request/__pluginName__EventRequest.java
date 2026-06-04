package ${packagePrefix}.${pluginName}.request;

import java.time.LocalDateTime;

public class ${pluginName}EventRequest {
    private LocalDateTime eventTime;

    // Constructors, getters, setters
    public ${pluginName}EventRequest() {}

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalDateTime eventTime) {
        this.eventTime = eventTime;
    }
}
