package gj.pf4j;

import gj.pf4j.descriptor.GJPluginDescriptor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationContext;

@Setter
@Getter
@Builder
public class GJPluginContext {
    public String pluginId;
    public ClassLoader classLoader;
    public ApplicationContext applicationContext;
    public GJPluginDescriptor descriptor;
    public boolean everStarted;
}
