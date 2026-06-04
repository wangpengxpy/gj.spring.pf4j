package gj.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Configuration
public class MainMessageSourceCfg {
    @Bean
    public ReloadableResourceBundleMessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setBasename("classpath:i18n/messages");
        Properties commonProps = loadCommonMessages();
        messageSource.setCommonMessages(commonProps);
        return messageSource;
    }

    private Properties loadCommonMessages() {
        Properties props = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("i18n/common/messages.properties");
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            props.load(inputStreamReader);
        } catch (IOException e) {
            throw new RuntimeException("加载公共翻译失败", e);
        } catch (NullPointerException e) {
            return new Properties();
        }
        return props;
    }
}