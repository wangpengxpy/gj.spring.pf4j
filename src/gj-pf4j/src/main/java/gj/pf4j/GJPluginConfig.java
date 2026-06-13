/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import gj.pf4j.modelmapper.GJPluginModelMapperRegistry;
import gj.pf4j.mybatis.GJPluginMybatisSqlSessionManager;
import gj.pf4j.mybatis.interceptor.GJSqlKeywordQuoteInterceptor;
import gj.pf4j.mybatis.interceptor.GJTableKeywordRegistry;
import gj.pf4j.utils.GJPluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.AntPathMatcher;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class GJPluginConfig {

    private static final Logger log = LoggerFactory.getLogger(GJPluginConfig.class);

    private static final String pluginDir = "plugins";

    private final Environment env;

    public GJPluginConfig(Environment env) {
        this.env = env;
    }

    @Bean
    public GJPluginManager pluginManager(ApplicationContext applicationContext) {
        Path pluginsDir;
        if (env.acceptsProfiles(Profiles.of("dev | debug"))) {
            String currentDir = System.getProperty("user.dir");
            log.info("current working directory: {}", currentDir);
            pluginsDir = Paths.get(currentDir, pluginDir);
        } else {
            ApplicationHome applicationHome = new ApplicationHome(getClass());
            pluginsDir = applicationHome.getDir().toPath();
        }
        GJPluginUtils.validatePluginDirectory(pluginsDir);
        log.info("plugin directory path: {}", pluginsDir.toAbsolutePath());
        GJPluginManager pluginManager = new GJPluginManager(pluginsDir);
        pluginManager.setApplicationContext(applicationContext);
        return pluginManager;
    }

    @Bean("pluginRequestMappingHandlerMapping")
    @ConditionalOnWebApplication(type = Type.SERVLET)
    public GJPluginRequestMappingHandlerMapping pluginRequestMappingHandlerMapping() {
        GJPluginRequestMappingHandlerMapping handlerMapping = new GJPluginRequestMappingHandlerMapping();
        handlerMapping.setOrder(-1);
        AntPathMatcher pathMatcher = new AntPathMatcher();
        pathMatcher.setCaseSensitive(false);
        handlerMapping.setPathMatcher(pathMatcher);
        handlerMapping.setPatternParser(null);
        return handlerMapping;
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(MybatisPlusInterceptor.class)
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            GJSqlKeywordQuoteInterceptor sqlKeywordQuoteInterceptor) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(sqlKeywordQuoteInterceptor);
        return interceptor;
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    public GJTableKeywordRegistry tableKeywordRegistry() {
        return new GJTableKeywordRegistry();
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    public GJSqlKeywordQuoteInterceptor sqlKeywordQuoteInterceptor(
            GJTableKeywordRegistry registry) {
        return new GJSqlKeywordQuoteInterceptor(registry);
    }

    @Bean
    public GJPluginMybatisSqlSessionManager pluginMybatisSqlSessionManager(
            DataSource dataSource,
            MybatisPlusInterceptor mybatisPlusInterceptor) {
        return new GJPluginMybatisSqlSessionManager(dataSource, mybatisPlusInterceptor);
    }

    @Bean
    public GJPluginModelMapperRegistry pluginModelMapperRegistry() {
        return new GJPluginModelMapperRegistry();
    }

    @Bean
    public GJPluginService pluginService(GJPluginManager pluginManager) {
        return new GJPluginService(pluginManager);
    }
}