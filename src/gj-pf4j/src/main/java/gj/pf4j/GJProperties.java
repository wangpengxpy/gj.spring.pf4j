package gj.pf4j;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("gj")
public class GJProperties {

    private HotReload hotReload = HotReload.WATCH;
    private String pluginsDir;

    public static final String DEFAULT_PLUGIN_DIR = "plugins";

    public enum HotReload { MANUAL, WATCH }
}
