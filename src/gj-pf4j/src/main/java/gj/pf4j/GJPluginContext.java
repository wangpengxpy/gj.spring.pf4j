package gj.pf4j;

import gj.pf4j.descriptor.GJPluginDescriptor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.context.ApplicationContext;

@Getter
@Builder
public class GJPluginContext {
    private String pluginId;
    private ClassLoader classLoader;
    private ApplicationContext applicationContext;
    private GJPluginDescriptor descriptor;
    private boolean everStarted;

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
}
