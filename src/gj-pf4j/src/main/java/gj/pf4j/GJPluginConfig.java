/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.pf4j.anonymous.DefaultPluginAnonymousPathRegistry;
import gj.pf4j.anonymous.PluginAnonymousPathRegistrar;
import gj.pf4j.anonymous.PluginAnonymousPathRegistry;
import gj.pf4j.eventbus.GJPluginLocalEventBus;
import gj.pf4j.hotreload.GJPluginHotReloadManager;
import gj.pf4j.i18n.GJI18nProperties;
import gj.pf4j.jpa.GJPluginJpaEntityManagerManager;
import gj.pf4j.jpa.GJPluginJpaProperties;
import gj.pf4j.modelmapper.GJPluginModelMapperRegistry;
import gj.pf4j.mybatis.GJPluginMybatisSqlSessionManager;
import gj.pf4j.mybatis.interceptor.GJSqlKeywordQuoteInterceptor;
import gj.pf4j.mybatis.interceptor.GJTableKeywordRegistry;
import gj.pf4j.socketio.GJSocketIOProperties;
import gj.pf4j.utils.GJPluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.util.AntPathMatcher;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@EnableConfigurationProperties({GJI18nProperties.class, GJSocketIOProperties.class})
public class GJPluginConfig {

    private static final Logger log = LoggerFactory.getLogger(GJPluginConfig.class);

    private final Environment env;
    private final ObjectMapper objectMapper;

    public GJPluginConfig(Environment env,
                          @Autowired(required = false) ObjectMapper objectMapper) {
        this.env = env;
        this.objectMapper = objectMapper;
    }

    @Bean
    public GJPluginManager pluginManager(ApplicationContext applicationContext,
                                          GJProperties properties) {
        Path pluginsDir;
        String customDir = properties.getPluginsDir();
        if (customDir != null && !customDir.isBlank()) {
            pluginsDir = Paths.get(customDir).toAbsolutePath().normalize();
            if (!Files.isDirectory(pluginsDir)) {
                throw new IllegalStateException(
                        "Configured plugin dir does not exist: " + pluginsDir);
            }
        } else if (env.acceptsProfiles(Profiles.of("dev | debug"))) {
            String currentDir = System.getProperty("user.dir");
            log.info("current working directory: {}", currentDir);
            pluginsDir = Paths.get(currentDir, GJProperties.DEFAULT_PLUGIN_DIR);
        } else {
            ApplicationHome applicationHome = new ApplicationHome(getClass());
            pluginsDir = applicationHome.getDir().toPath().resolve(GJProperties.DEFAULT_PLUGIN_DIR);
        }
        GJPluginUtils.validatePluginDirectory(pluginsDir);
        log.info("plugin directory path: {}", pluginsDir.toAbsolutePath());
        GJPluginManager pluginManager = new GJPluginManager(pluginsDir);
        pluginManager.setApplicationContext(applicationContext);
        return pluginManager;
    }

    @Bean("pluginRequestMappingHandlerMapping")
    @ConditionalOnWebApplication(type = Type.SERVLET)
    public GJPluginRequestMappingHandlerMapping pluginRequestMappingHandlerMapping(
            PluginAnonymousPathRegistrar anonymousPathRegistrar) {
        GJPluginRequestMappingHandlerMapping handlerMapping = new GJPluginRequestMappingHandlerMapping();
        handlerMapping.setOrder(-1);
        AntPathMatcher pathMatcher = new AntPathMatcher();
        pathMatcher.setCaseSensitive(false);
        handlerMapping.setPathMatcher(pathMatcher);
        handlerMapping.setPatternParser(null);
        handlerMapping.setAnonymousPathRegistry(anonymousPathRegistrar);
        return handlerMapping;
    }

    @Bean
    @ConditionalOnClass(MybatisPlusInterceptor.class)
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
    @ConditionalOnClass(MybatisPlusInterceptor.class)
    @ConditionalOnBean(DataSource.class)
    public GJSqlKeywordQuoteInterceptor sqlKeywordQuoteInterceptor(
            GJTableKeywordRegistry registry) {
        return new GJSqlKeywordQuoteInterceptor(registry);
    }

    @Bean
    @ConditionalOnClass(MybatisPlusInterceptor.class)
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
    public PluginAnonymousPathRegistry pluginAnonymousPathRegistry() {
        return new DefaultPluginAnonymousPathRegistry();
    }

    @Bean
    public PluginAnonymousPathRegistrar pluginAnonymousPathRegistrar(
            PluginAnonymousPathRegistry registry) {
        return (PluginAnonymousPathRegistrar) registry;
    }

    @Bean
    @ConditionalOnMissingBean(GJProperties.class)
    public GJProperties gjProperties() {
        return new GJProperties();
    }

    @Bean
    public GJPluginService pluginService(GJPluginManager pluginManager) {
        return new GJPluginService(pluginManager);
    }

    @Bean
    public GJPluginHotReloadManager pluginHotReloadManager(GJPluginService pluginService,
                                                            GJPluginManager pluginManager,
                                                            GJProperties properties) {
        try {
            Path pluginsDir = pluginManager.getPluginsRoots().get(0);
            return new GJPluginHotReloadManager(pluginService, pluginsDir, properties);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to create GJPluginHotReloadManager", e);
        }
    }

    /** Create EventBus bean. Host app can override by defining its own. */
    @Bean
    @ConditionalOnMissingBean(GJPluginLocalEventBus.class)
    public GJPluginLocalEventBus pluginLocalEventBus() {
        ObjectMapper mapper = (objectMapper != null)
                ? objectMapper
                : GJJackson.createDefaultObjectMapper();
        return new GJPluginLocalEventBus(mapper);
    }

    // ── JPA Beans (activated by host adding hibernate-core dependency) ──────
    @Bean
    @ConditionalOnClass(name = "org.hibernate.jpa.HibernatePersistenceProvider")
    @ConditionalOnMissingBean(JpaVendorAdapter.class)
    public JpaVendorAdapter jpaVendorAdapter() {
        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setShowSql(false);
        return adapter;
    }

    @Bean
    @ConditionalOnBean(JpaVendorAdapter.class)
    @ConditionalOnMissingBean(GJPluginJpaProperties.class)
    public GJPluginJpaProperties pluginJpaProperties() {
        return new GJPluginJpaProperties();
    }

    @Bean
    @ConditionalOnBean({DataSource.class, JpaVendorAdapter.class})
    public GJPluginJpaEntityManagerManager pluginJpaEntityManagerManager(
            DataSource dataSource,
            JpaVendorAdapter jpaVendorAdapter,
            GJPluginJpaProperties properties) {
        return new GJPluginJpaEntityManagerManager(dataSource, jpaVendorAdapter, properties);
    }
}
