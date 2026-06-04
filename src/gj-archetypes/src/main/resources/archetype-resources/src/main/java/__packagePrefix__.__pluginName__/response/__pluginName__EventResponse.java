package ${packagePrefix}.${pluginName}.response;

import java.time.LocalDateTime;

public class ${pluginName}EventResponse {
    private Long id;
    private LocalDateTime eventTime;

    // Constructors
    public ${pluginName}EventResponse() {}

    public ${pluginName}EventResponse(Long id, LocalDateTime eventTime) {
        this.id = id;
        this.eventTime = eventTime;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
}
