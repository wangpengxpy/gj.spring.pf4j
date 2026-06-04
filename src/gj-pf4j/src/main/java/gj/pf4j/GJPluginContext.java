package gj.pf4j;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationContext;

@Setter
@Getter
@Builder
class GJPluginContext {
    public String pluginId;
    public String path;
    public String version;
    public String description;
    public ClassLoader classLoader;
    public ApplicationContext applicationContext;
    public ApplicationContext mainApplicationContext;
    public boolean mainApplicationStarted;
}
