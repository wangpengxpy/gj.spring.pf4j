/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.mybatis;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import gj.pf4j.jpa.GJPluginJpaEntityManagerManager;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.lang.NonNull;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GJPluginMybatisSqlSessionManager {

    private static final Logger log = LoggerFactory.getLogger(GJPluginMybatisSqlSessionManager.class);

    private final ConcurrentHashMap<String, SqlSessionTemplate> pluginSessionCache = new ConcurrentHashMap<>();
    private final DataSource dataSource;
    private final MybatisPlusInterceptor mybatisPlusInterceptor;

    /**
     * Shared across all plugins to avoid repeated DNS/network-interface probing
     * that occurs when MyBatis-Plus internally creates a {@code DefaultIdentifierGenerator}
     * per {@code SqlSessionFactory}. Each probe may block for seconds; with N plugins
     * the cost is paid N times without this singleton.
     */
    private volatile IdentifierGenerator sharedIdentifierGenerator;

    public GJPluginMybatisSqlSessionManager(@NonNull DataSource dataSource,
                                            MybatisPlusInterceptor mybatisPlusInterceptor) {
        this.dataSource = dataSource;
        this.mybatisPlusInterceptor = mybatisPlusInterceptor;
        log.info("PluginMybatisSqlSessionManager initialized with shared DataSource: {}",
                dataSource != null ? dataSource.getClass().getSimpleName() : "null");
    }

    /**
     * Lazily create one shared {@link IdentifierGenerator} for all plugins.
     * Uses UUID-hash-based workerId to avoid DNS/network-interface probing,
     * so the first plugin triggers the initialization once and all subsequent
     * plugins reuse the same instance.
     */
    private IdentifierGenerator resolveSharedIdentifierGenerator() {
        IdentifierGenerator local = sharedIdentifierGenerator;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (sharedIdentifierGenerator != null) {
                return sharedIdentifierGenerator;
            }
            int workerId = UUID.randomUUID().hashCode() & 31;
            int dataCenterId = 1;
            sharedIdentifierGenerator = new DefaultIdentifierGenerator(workerId, dataCenterId);
            log.info("Created shared IdentifierGenerator (workerId={}, dataCenterId={})", workerId, dataCenterId);
            return sharedIdentifierGenerator;
        }
    }

    public void initializeMyBatisForPlugin(String pluginId,
                                           GenericApplicationContext context) {

        String basePackage = getDao(pluginId);

        // 0. Avoid repeated initialization
        if (pluginSessionCache.containsKey(pluginId)) {
            log.warn("GJPluginMybatisSqlSessionManager already initialized for plugin '{}'. Skipping.", pluginId);
            return;
        }

        long startTime = System.currentTimeMillis();
        log.info("Starting MyBatis initialization for plugin: '{}', scanning DAO package: '{}'", pluginId, basePackage);

        try {
            // 1. Create SqlSessionFactory
            SqlSessionFactory sqlSessionFactory = createSqlSessionFactory(pluginId);
            log.info("SqlSessionFactory created: {}", sqlSessionFactory);

            // 2. Create SqlSessionTemplate and cache it
            SqlSessionTemplate sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
            pluginSessionCache.put(pluginId, sqlSessionTemplate);
            log.info("SqlSessionTemplate created and cached for plugin: '{}'", pluginId);

            // 3. Get the BeanFactory of the plugin context
            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();

            // 4. Register the cached SqlSessionTemplate instance to the Spring container
            String sqlSessionBeanName = pluginId + "_sqlSessionTemplate";
            if (!beanFactory.containsBean(sqlSessionBeanName)) {
                beanFactory.registerSingleton(sqlSessionBeanName, sqlSessionTemplate);
                log.info("Registered cached SqlSessionTemplate as bean '{}' in plugin context", sqlSessionBeanName);
            } else {
                log.warn("SqlSessionTemplate bean '{}' already exists in plugin context, skipping registration", sqlSessionBeanName);
            }

            // 5. Register TransactionManager for the plugin
            // If JPA is active, the JpaTransactionManager (registered by GJPluginJpaEntityManagerManager)
            // will handle both JPA and MyBatis transactions since they share the same DataSource.
            // Only register a standalone DataSourceTransactionManager when JPA is not available.
            boolean jpaActive = isJpaActive(context);
            String txManagerBeanName = pluginId + "_transactionManager";
            if (!jpaActive) {
                if (!beanFactory.containsBeanDefinition(txManagerBeanName)) {
                    BeanDefinitionBuilder tmBuilder = BeanDefinitionBuilder
                            .genericBeanDefinition(DataSourceTransactionManager.class);
                    tmBuilder.addConstructorArgValue(dataSource);
                    tmBuilder.setPrimary(true);
                    beanFactory.registerBeanDefinition(txManagerBeanName, tmBuilder.getBeanDefinition());
                    log.info("Registered DataSourceTransactionManager (@Primary) as bean '{}' for plugin [{}]",
                            txManagerBeanName, pluginId);
                }
            } else {
                log.info("JPA is active, skipping DataSourceTransactionManager registration for plugin [{}] " +
                        "(JpaTransactionManager will be registered by GJPluginJpaEntityManagerManager)", pluginId);
            }

            // 6. Scan DAO packages via MapperScannerConfigurer
            String scannerBeanName = pluginId + "_mapperScannerConfigurer";
            if (!beanFactory.containsBeanDefinition(scannerBeanName)) {
                BeanDefinitionBuilder scannerBuilder = BeanDefinitionBuilder
                        .genericBeanDefinition(MapperScannerConfigurer.class);
                scannerBuilder.addPropertyValue("basePackage", basePackage);
                scannerBuilder.addPropertyValue("sqlSessionTemplateBeanName", sqlSessionBeanName);
                beanFactory.registerBeanDefinition(scannerBeanName, scannerBuilder.getBeanDefinition());
                log.debug("Registered MapperScannerConfigurer as bean '{}' to scan package: {}",
                        scannerBeanName, basePackage);
            } else {
                log.warn("MapperScannerConfigurer bean '{}' already exists, skipping registration", scannerBeanName);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully initialized MyBatis for plugin: '{}' (took {} ms)", pluginId, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to initialize MyBatis for plugin: '{}' (took {} ms). Cause: {}",
                    pluginId, duration, e.getMessage(), e);
            cleanupPluginResources(pluginId);
            throw new RuntimeException("MyBatis initialization failed for plugin: " + pluginId, e);
        }
    }

    private static String getDao(String pluginId) {
        if (pluginId == null || pluginId.trim().isEmpty()) {
            throw new IllegalArgumentException("Plugin ID must not be null or empty");
        }
        String normalizedPluginId = pluginId.replace('-', '.');
        return normalizedPluginId + ".dao";
    }

    private SqlSessionFactory createSqlSessionFactory(String pluginId) throws Exception {
        log.info("Creating SqlSessionFactory for plugin: '{}'", pluginId);

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCacheEnabled(false);
        configuration.setLazyLoadingEnabled(false);
        log.info("MyBatis configuration: camelCase={}, cache={}, lazyLoading={}",
                true, false, false);

        MybatisSqlSessionFactoryBean factory = getMybatisSqlSessionFactoryBean(configuration, pluginId);
        SqlSessionFactory sqlSessionFactory = factory.getObject();
        log.trace("SqlSessionFactory built successfully for plugin: '{}'", pluginId);
        return sqlSessionFactory;
    }

    private MybatisSqlSessionFactoryBean getMybatisSqlSessionFactoryBean(
            MybatisConfiguration configuration, String pluginId) {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        factory.setPlugins(mybatisPlusInterceptor);
        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setIdentifierGenerator(resolveSharedIdentifierGenerator());
        factory.setGlobalConfig(globalConfig);
        factory.setTransactionFactory(new SpringManagedTransactionFactory());
        log.info("MyBatis factory configured with plugin: '{}'", pluginId);
        return factory;
    }

    public void cleanupPluginResources(String pluginId) {
        if (pluginId != null) {
            SqlSessionTemplate removed = pluginSessionCache.remove(pluginId);
            if (removed != null) {
                log.info("Cleaned up MyBatis resources for plugin: '{}'", pluginId);
            } else {
                log.debug("No cached SqlSessionTemplate found for plugin: '{}'", pluginId);
            }
        }
    }

    private static boolean isJpaActive(GenericApplicationContext pluginContext) {
        ApplicationContext parent = pluginContext.getParent();
        if (parent != null) {
            return !parent.getBeansOfType(GJPluginJpaEntityManagerManager.class).isEmpty();
        }
        return false;
    }

    @PreDestroy
    public void destroy() {
        int count = pluginSessionCache.size();
        pluginSessionCache.clear();
        log.info("CloseOperation PluginMybatisSqlSessionManager. Cleared {} plugin session(s).", count);
    }
}