package ${packagePrefix}.${pluginName};

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 插件配置
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "${packagePrefix}.${pluginName}")
public class ${pluginName}Config {
    public boolean enabled;
    public String value;
}
