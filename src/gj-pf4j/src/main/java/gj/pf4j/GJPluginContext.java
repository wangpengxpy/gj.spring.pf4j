package gj.pf4j;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationContext;

@Setter
@Getter
@Builder
public class GJPluginContext {
    public String pluginId;
    public String path;
    public String version;
    public String description;
    public int order;
    public ClassLoader classLoader;
    public ApplicationContext applicationContext;
    public gj.pf4j.descriptor.GJPluginDescriptor descriptor;
    public boolean everStarted;
}
