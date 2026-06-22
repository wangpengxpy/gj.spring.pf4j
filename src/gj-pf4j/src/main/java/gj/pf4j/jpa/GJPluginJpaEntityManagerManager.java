package gj.pf4j.jpa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.lang.NonNull;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import javax.annotation.PreDestroy;
import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GJPluginJpaEntityManagerManager {

    private static final Logger log = LoggerFactory.getLogger(GJPluginJpaEntityManagerManager.class);

    private final Set<String> initializedPlugins = ConcurrentHashMap.newKeySet();
    private final DataSource dataSource;
    private final JpaVendorAdapter jpaVendorAdapter;
    private final GJPluginJpaProperties defaultProperties;
    private final GJJpaRepositoryScanner repositoryScanner = new GJJpaRepositoryScanner();

    public GJPluginJpaEntityManagerManager(@NonNull DataSource dataSource,
                                           JpaVendorAdapter jpaVendorAdapter,
                                           GJPluginJpaProperties defaultProperties) {
        this.dataSource = dataSource;
        this.jpaVendorAdapter = jpaVendorAdapter;
        this.defaultProperties = defaultProperties;
        log.info("GJPluginJpaEntityManagerManager initialized with shared DataSource: {}",
                dataSource.getClass().getSimpleName());
    }

    public void initializeJpaForPlugin(String pluginId,
                                        GenericApplicationContext context) {

        String normalizedPluginId = pluginId.replace('-', '.');
        String entityPackage = normalizedPluginId + ".entity";
        String repositoryPackage = normalizedPluginId + ".repository";

        if (initializedPlugins.contains(pluginId)) {
            log.warn("JPA already initialized for plugin '{}'. Skipping.", pluginId);
            return;
        }

        long startTime = System.currentTimeMillis();
        log.info("Starting JPA initialization for plugin: '{}', entity package: '{}', repository package: '{}'",
                pluginId, entityPackage, repositoryPackage);

        try {
            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) context.getBeanFactory();

            // 1. Register EntityManagerFactory BeanDefinition
            String emfBeanName = pluginId + "_entityManagerFactory";
            BeanDefinitionBuilder emfBuilder = BeanDefinitionBuilder
                    .genericBeanDefinition(LocalContainerEntityManagerFactoryBean.class);
            emfBuilder.addPropertyValue("dataSource", dataSource);
            emfBuilder.addPropertyValue("packagesToScan", new String[]{entityPackage});
            emfBuilder.addPropertyValue("jpaVendorAdapter", jpaVendorAdapter);
            emfBuilder.addPropertyValue("jpaPropertyMap", defaultProperties.toJpaPropertyMap());
            emfBuilder.addPropertyValue("persistenceUnitName", pluginId);
            emfBuilder.setPrimary(true);
            beanFactory.registerBeanDefinition(emfBeanName, emfBuilder.getBeanDefinition());
            log.info("Registered EntityManagerFactory bean '{}' for plugin: '{}'", emfBeanName, pluginId);

            // 2. Register JpaTransactionManager BeanDefinition (@Primary, replaces MyBatis DataSourceTransactionManager)
            String txManagerBeanName = pluginId + "_transactionManager";
            BeanDefinitionBuilder tmBuilder = BeanDefinitionBuilder
                    .genericBeanDefinition(JpaTransactionManager.class);
            tmBuilder.addPropertyReference("entityManagerFactory", emfBeanName);
            tmBuilder.setPrimary(true);
            beanFactory.registerBeanDefinition(txManagerBeanName, tmBuilder.getBeanDefinition());
            log.info("Registered JpaTransactionManager bean '{}' (@Primary) for plugin: '{}'",
                    txManagerBeanName, pluginId);

            // 3. Register PersistenceExceptionTranslationPostProcessor
            String etBeanName = pluginId + "_persistenceExceptionTranslator";
            if (!beanFactory.containsBean(etBeanName)) {
                beanFactory.registerSingleton(etBeanName,
                        new PersistenceExceptionTranslationPostProcessor());
                log.debug("Registered PersistenceExceptionTranslationPostProcessor for plugin: '{}'", pluginId);
            }

            // 4. Scan and register JPA repository beans
            List<Class<?>> repositoryInterfaces = repositoryScanner.scan(repositoryPackage, context.getClassLoader());
            for (Class<?> repoInterface : repositoryInterfaces) {
                String repoBeanName = generateRepoBeanName(pluginId, repoInterface);
                if (!beanFactory.containsBeanDefinition(repoBeanName)) {
                    BeanDefinition repoDef = repositoryScanner.createRepositoryBeanDefinition(
                            repoInterface, emfBeanName);
                    beanFactory.registerBeanDefinition(repoBeanName, repoDef);
                    log.debug("Registered JPA repository bean '{}' for interface: {}",
                            repoBeanName, repoInterface.getName());
                }
            }

            initializedPlugins.add(pluginId);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Successfully initialized JPA for plugin: '{}', registered {} repository beans (took {} ms)",
                    pluginId, repositoryInterfaces.size(), duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to initialize JPA for plugin: '{}' (took {} ms). Cause: {}",
                    pluginId, duration, e.getMessage(), e);
            throw new RuntimeException("JPA initialization failed for plugin: " + pluginId, e);
        }
    }

    private String generateRepoBeanName(String pluginId, Class<?> repoInterface) {
        String shortName = repoInterface.getSimpleName();
        int lastDot = pluginId.lastIndexOf('.');
        String suffix = lastDot >= 0 ? pluginId.substring(lastDot + 1) : pluginId;
        return suffix + "." + Character.toLowerCase(shortName.charAt(0)) + shortName.substring(1);
    }

    public void cleanupPluginResources(String pluginId,
                                        ApplicationContext pluginContext) {
        initializedPlugins.remove(pluginId);

        String emfBeanName = pluginId + "_entityManagerFactory";
        try {
            if (pluginContext.containsBean(emfBeanName)) {
                EntityManagerFactory emf = pluginContext.getBean(emfBeanName, EntityManagerFactory.class);
                if (emf != null && emf.isOpen()) {
                    emf.close();
                    log.info("Closed EntityManagerFactory for plugin: '{}'", pluginId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to close EntityManagerFactory for plugin: '{}': {}", pluginId, e.getMessage());
        }
    }

    @PreDestroy
    public void destroy() {
        int count = initializedPlugins.size();
        initializedPlugins.clear();
        log.info("GJPluginJpaEntityManagerManager destroyed ({} plugin(s) tracked). " +
                "EMF cleanup handled by per-plugin context.close().", count);
    }
}
