package gj.pf4j.i18n;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("gj.i18n")
public class GJI18nProperties {
    private int cacheSeconds = 86400;
    private boolean useCodeAsDefaultMessage = true;
}
