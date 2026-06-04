package gj.pf4j.i18n;

import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class GJPluginReloadableMessageSource extends ReloadableResourceBundleMessageSource {
    private final ReloadableResourceBundleMessageSource mainAppMessageSource;

    public GJPluginReloadableMessageSource(String pluginBasename,
                                           ClassLoader pluginClassLoader,
                                           ReloadableResourceBundleMessageSource mainAppMessageSource) {
        this.setDefaultEncoding(StandardCharsets.UTF_8.name());
        this.setBasename("classpath:" + pluginBasename);
        this.setUseCodeAsDefaultMessage(true);
        this.setCacheSeconds(86400);
        this.setResourceLoader(new DefaultResourceLoader(pluginClassLoader));
        this.mainAppMessageSource = mainAppMessageSource;

    }

    @Override
    @Nullable
    protected String getMessageInternal(String code, Object[] args, Locale locale) {
        String result = super.getMessageInternal(code, args, locale);
        if (StringUtils.hasLength(result)) {
            return result;
        } else {
            result = mainAppMessageSource.getMessage(code, args, locale);
        }
        return result == null ? code : result;
    }
}
