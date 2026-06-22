/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j;

import gj.pf4j.eventbus.GJPluginLocalEventBus;
import gj.pf4j.i18n.GJPluginReloadableMessageSource;
import gj.pf4j.jpa.GJPluginJpaEntityManagerManager;
import gj.pf4j.migration.GJPluginModelMigrator;
import gj.pf4j.modelmapper.GJPluginModelMapperRegistry;
import gj.pf4j.mybatis.GJPluginMybatisSqlSessionManager;
import gj.pf4j.mybatis.interceptor.GJTableKeywordProvider;
import gj.pf4j.mybatis.interceptor.GJTableKeywordRegistry;
import gj.pf4j.openapi.GJPluginOpenApiConfig;
import gj.pf4j.openapi.GJPluginOpenApiInfo;
import gj.pf4j.quartzjob.PluginJobManager;
import gj.pf4j.socketio.GJHub;
import gj.pf4j.socketio.GJHubManager;
import gj.pf4j.webflux.GJPluginWebFluxRequestMappingHandlerMapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePropertySource;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

class GJPluginLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GJPluginLifecycle.class);

    private final GJPluginContext pluginContext;

    private final List<RegistrationOperation> registrationOperations;

    private final List<UnregistrationOperation> unregistrationOperations;

    public GJPluginLifecycle(GJPluginContext pluginContext) {
        this.pluginContext = pluginContext;
        this.registrationOperations = initRegistrationOperations();
        this.unregistrationOperations = initUnregistrationOperations();
    }

    @FunctionalInterface
    private interface RegistrationOperation {
        void execute(AnnotationConfigApplicationContext applicationContext);
    }

    @FunctionalInterface
    private interface UnregistrationOperation {
        void execute();
    }

    private List<RegistrationOperation> initRegistrationOperations() {
        List<RegistrationOperation> ops = new ArrayList<>();
        ops.add(this::registerResource);
        ops.add(this::registerI18NMessageSource);
        ops.add(this::registerMybatis);
        ops.add(this::registerJpa);
        ops.add(this::registerAutoMigration);
        return ops;
    }

    private List<UnregistrationOperation> initUnregistrationOperations() {
        List<UnregistrationOperation> ops = new ArrayList<>();
        ops.add(this::unregisterControllers);
        ops.add(this::unregisterHubs);
        ops.add(this::unregisterI18NMessageSource);
        ops.add(this::unregisterJpa);
        ops.add(this::unregisterMybatis);
        ops.add(this::unregisterEventListeners);
        ops.add(this::unregisterQuartzJobs);
        return ops;
    }

    void registerPluginResources(AnnotationConfigApplicationContext applicationContext) {
        String pluginId = pluginContext.getPluginId();
        log.info("[Plugin: {}] Start registering plugin resources, total {} operations", pluginId, registrationOperations.size());
        long startTime = System.currentTimeMillis();
        try {
            for (int i = 0; i < registrationOperations.size(); i++) {
                RegistrationOperation op = registrationOperations.get(i);
                log.debug("[Plugin: {}] Execute registration operation [{}/{}]", pluginId, i + 1, registrationOperations.size());
                op.execute(applicationContext);
            }
            long cost = System.currentTimeMillis() - startTime;
            log.info("[Plugin: {}] Plugin resource registration completed, time elapsed: {}ms", pluginId, cost);
        } catch (Exception e) {
            log.error("[Plugin: {}] Plugin resource registration failed, terminate immediately", pluginContext.getPluginId(), e);
            throw new RuntimeException("Plugin resource registration exception", e);
        }
    }

    void unregisterPluginResources() {
        String pluginId = pluginContext.getPluginId();
        log.info("[Plugin: {}] Start unregistering plugin resources, total {} operations", pluginId, unregistrationOperations.size());
        long startTime = System.currentTimeMillis();
        try {
            for (int i = 0; i < unregistrationOperations.size(); i++) {
                UnregistrationOperation op = unregistrationOperations.get(i);
                log.debug("[Plugin: {}] Execute unregistration operation [{}/{}]", pluginId, i + 1, unregistrationOperations.size());
                op.execute();
            }
            long cost = System.currentTimeMillis() - startTime;
            log.info("[Plugin: {}] Plugin resource unregistration completed, time elapsed: {}ms", pluginId, cost);
        } catch (Exception e) {
            log.error("[Plugin: {}] Plugin resource unregistration failed (some resources may not be cleaned up)", pluginContext.getPluginId(), e);
        }
    }

    private void unregisterHubs() {
        Map<String, GJHub> hubs =
                pluginContext.getApplicationContext().getBeansOfType(GJHub.class);
        if (hubs.isEmpty()) {
            return;
        }
        GenericApplicationContext context = (GenericApplicationContext) pluginContext.getMainApplicationContext();
        GJHubManager hubManager = context.getBean(GJHubManager.class);
        for (String hubName : hubs.keySet()) {
            hubManager.unregisterHub(hubName);
        }
    }

    private void registerResource(AnnotationConfigApplicationContext applicationContext) {
        String pluginId = pluginContext.getPluginId();
        try {
            String resourceName = pluginId + ".properties";
            Resource resource = new ClassPathResource(resourceName, pluginContext.getClassLoader());
            if (resource.exists()) {
                applicationContext.getEnvironment()
                        .getPropertySources()
                        .addFirst(new ResourcePropertySource(resource));
            } else {
                log.warn("Resource '{}' does not exist in classpath", resourceName);
            }
        } catch (Exception e) {
            log.error("Failed to load {}.properties", pluginId, e);
            throw new RuntimeException(String.format("Failed to load %s.properties", pluginId), e);
        }
    }

    private void registerI18NMessageSource(AnnotationConfigApplicationContext applicationContext) {
        String pluginId = pluginContext.getPluginId();
        long startTime = System.currentTimeMillis();
        log.debug("[il8n] Starting registration for plugin: '{}'", pluginId);
        try {
            String il8nPluginBeanName = "plugin_i18n_" + pluginId;
            ConfigurableListableBeanFactory beanFactory = applicationContext.getBeanFactory();
            // 1. Destroy the bean first if it already exists
            if (beanFactory.containsBean(il8nPluginBeanName)) {
                beanFactory.destroyBean(il8nPluginBeanName);
            }
            // 2. Get the main application's MessageSource (contains common translations)
            ReloadableResourceBundleMessageSource mainMs = pluginContext.getMainApplicationContext().getBean("messageSource", ReloadableResourceBundleMessageSource.class);
            // 3. Initialize the plugin's MessageSource
            GJPluginReloadableMessageSource pluginMs = new GJPluginReloadableMessageSource(
                    "i18n/messages",
                    pluginContext.getClassLoader(),
                    mainMs
            );
            // 4. Register the bean (singleton)
            beanFactory.registerSingleton(il8nPluginBeanName, pluginMs);
            log.debug("[il8n] Registered message source bean: '{}' for plugin: '{}'", il8nPluginBeanName, pluginId);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[il8n] Successfully registered resources for plugin: '{}' (took {} ms)",
                    pluginId, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[il8n] Failed to register  resources for plugin: '{}' (took {} ms). " +
                            "Error: {}",
                    pluginId, duration, e.getMessage(), e);
            throw new RuntimeException("il8n registration failed for plugin: " + pluginId, e);
        }
    }

    private void registerMybatis(AnnotationConfigApplicationContext applicationContext) {
        String pluginId = pluginContext.getPluginId();
        GJPluginMybatisSqlSessionManager mybatisRegistry = pluginContext.getMainApplicationContext().getBean(GJPluginMybatisSqlSessionManager.class);
        mybatisRegistry.initializeMyBatisForPlugin(pluginId, applicationContext);
    }

    private void registerJpa(AnnotationConfigApplicationContext applicationContext) {
        String pluginId = pluginContext.getPluginId();
        ApplicationContext mainCtx = pluginContext.getMainApplicationContext();
        var managers = mainCtx.getBeansOfType(GJPluginJpaEntityManagerManager.class);
        if (managers.isEmpty()) {
            log.debug("[Plugin: {}] JPA EntityManagerManager not available (Hibernate not on classpath), skipping JPA initialization", pluginId);
            return;
        }
        GJPluginJpaEntityManagerManager jpaManager = managers.values().iterator().next();
        jpaManager.initializeJpaForPlugin(pluginId, applicationContext);
    }

    private void registerAutoMigration(AnnotationConfigApplicationContext applicationContext) {
        GenericApplicationContext mainCtx = (GenericApplicationContext) pluginContext.getMainApplicationContext();
        if (mainCtx.getBeansOfType(GJPluginModelMigrator.class).isEmpty()) {
            return;
        }
        GJPluginModelMigrator migrator = mainCtx.getBean(GJPluginModelMigrator.class);
        migrator.migrate(pluginContext.getPluginId(), pluginContext.getClassLoader());
    }

    // ── Post-refresh registration ──────────────────────────────

    void registerPostStartResources(AnnotationConfigApplicationContext applicationContext) {
        GenericApplicationContext mainCtx = (GenericApplicationContext) pluginContext.getMainApplicationContext();
        registerControllers(applicationContext, mainCtx);
        registerHubs(applicationContext, mainCtx);
        registerModelMappers(applicationContext);
        registerEventListeners(applicationContext, mainCtx);
        registerQuartzJobs(applicationContext, mainCtx);
        registerPluginTableKeywords(applicationContext, mainCtx);
    }

    private void registerControllers(AnnotationConfigApplicationContext applicationContext,
                                     GenericApplicationContext mainCtx) {
        Set<Object> controllers;
        Map<String, GJPluginWebFluxRequestMappingHandlerMapping> webFluxMappings =
                mainCtx.getBeansOfType(GJPluginWebFluxRequestMappingHandlerMapping.class);
        if (!webFluxMappings.isEmpty()) {
            GJPluginWebFluxRequestMappingHandlerMapping handlerMapping =
                    webFluxMappings.values().iterator().next();
            controllers = handlerMapping.registerControllers(
                    pluginContext.getPluginId(), pluginContext.getApplicationContext());
        } else {
            Map<String, GJPluginRequestMappingHandlerMapping> mvcMappings =
                    mainCtx.getBeansOfType(GJPluginRequestMappingHandlerMapping.class);
            if (!mvcMappings.isEmpty()) {
                GJPluginRequestMappingHandlerMapping handlerMapping =
                        mvcMappings.values().iterator().next();
                controllers = handlerMapping.registerControllers(pluginContext);
            } else {
                log.debug("[Plugin: {}] No HandlerMapping found (non-web application), " +
                        "skipping controller registration.", pluginContext.getPluginId());
                return;
            }
        }
        registerPluginOpenApi(controllers);
    }

    private void registerHubs(AnnotationConfigApplicationContext applicationContext,
                              GenericApplicationContext mainCtx) {
        String pluginId = pluginContext.getPluginId();
        Map<String, GJHub> hubs =
                pluginContext.getApplicationContext().getBeansOfType(GJHub.class);
        if (hubs.isEmpty()) {
            log.debug("[Plugin: {}] No Hub Beans found, skipping registration.", pluginId);
            return;
        }
        if (mainCtx.getBeansOfType(GJHubManager.class).isEmpty()) {
            log.warn("[Plugin: {}] HubManager not registered, " +
                    "unable to register {} Hub instances.", pluginId, hubs.size());
            return;
        }
        if (log.isDebugEnabled()) {
            String hubNames = String.join(", ", hubs.keySet());
            log.debug("[Plugin: {}] Found {} Hubs, preparing for registration: {}",
                    pluginId, hubs.size(), hubNames);
        }
        GJHubManager hubManager = mainCtx.getBean(GJHubManager.class);
        hubManager.registerHubs(hubs.values());
    }

    private void registerModelMappers(AnnotationConfigApplicationContext applicationContext) {
        GJPluginModelMapperRegistry modelMapperRegistry =
                applicationContext.getBean(GJPluginModelMapperRegistry.class);
        try {
            modelMapperRegistry.registerModelMappers(
                    pluginContext.getPluginId(), pluginContext.getApplicationContext());
        } catch (Exception ignored) {
            log.debug("[Plugin: {}] ModelMapper registration skipped or failed: {}",
                    pluginContext.getPluginId(), ignored.getMessage());
        }
    }

    private void registerEventListeners(AnnotationConfigApplicationContext applicationContext,
                                         GenericApplicationContext mainCtx) {
        if (mainCtx.getBeansOfType(GJPluginLocalEventBus.class).isEmpty()) {
            log.debug("[Plugin: {}] GJPluginLocalEventBus not registered, skipping listener registration",
                    pluginContext.getPluginId());
            return;
        }
        GJPluginLocalEventBus eventBus = mainCtx.getBean(GJPluginLocalEventBus.class);
        eventBus.registerListeners(pluginContext.getPluginId(), pluginContext.getApplicationContext());
    }

    private void registerQuartzJobs(AnnotationConfigApplicationContext applicationContext,
                                    GenericApplicationContext mainCtx) {
        if (mainCtx.getBeansOfType(PluginJobManager.class).isEmpty()) {
            log.debug("[Plugin: {}] PluginJobManager not registered, skipping job registration",
                    pluginContext.getPluginId());
            return;
        }
        PluginJobManager jobManager = mainCtx.getBean(PluginJobManager.class);
        jobManager.registerJobs(pluginContext.getPluginId(), pluginContext.getApplicationContext());
    }

    private void registerPluginTableKeywords(AnnotationConfigApplicationContext applicationContext,
                                             GenericApplicationContext mainCtx) {
        Map<String, GJTableKeywordProvider> providers =
                applicationContext.getBeansOfType(GJTableKeywordProvider.class);
        if (providers.isEmpty()) {
            return;
        }
        if (mainCtx.getBeansOfType(GJTableKeywordRegistry.class).isEmpty()) {
            log.debug("[Plugin: {}] GJTableKeywordRegistry not registered, skipping keyword registration",
                    pluginContext.getPluginId());
            return;
        }
        GJTableKeywordRegistry registry = mainCtx.getBean(GJTableKeywordRegistry.class);
        Map<String, Set<String>> merged = new HashMap<>();
        for (GJTableKeywordProvider provider : providers.values()) {
            Map<String, Set<String>> entries = provider.getTableKeywords();
            if (entries != null) {
                merged.putAll(entries);
            }
        }
        if (!merged.isEmpty()) {
            registry.register(merged);
            log.info("[Plugin: {}] Registered {} table(s) keywords from plugin",
                    pluginContext.getPluginId(), merged.size());
        }
    }

    private void registerPluginOpenApi(Set<Object> controllers) {
        if (controllers.isEmpty()) {
            return;
        }
        GJPluginOpenApiInfo pluginOpenApiInfo = new GJPluginOpenApiInfo();
        pluginOpenApiInfo.setGroupName(pluginContext.getPluginId());
        List<String> controllerPackages = new ArrayList<>();
        List<Class<?>> controllerClasses = new ArrayList<>();
        for (Object controller : controllers) {
            controllerPackages.add(controller.getClass().getPackageName());
            controllerClasses.add(controller.getClass());
        }
        pluginOpenApiInfo.setControllerPackages(controllerPackages.stream().distinct().collect(Collectors.toList()));
        pluginOpenApiInfo.setControllerClasses(controllerClasses);
        GJPluginOpenApiConfig.registerPluginOpenApiBeans(
                pluginContext.getMainApplicationContext(), pluginOpenApiInfo);
    }

    // ── Unregistration ─────────────────────────────────────────

    private void unregisterJpa() {
        String pluginId = pluginContext.getPluginId();
        ApplicationContext mainCtx = pluginContext.getMainApplicationContext();
        var managers = mainCtx.getBeansOfType(GJPluginJpaEntityManagerManager.class);
        if (managers.isEmpty()) {
            return;
        }
        GJPluginJpaEntityManagerManager jpaManager = managers.values().iterator().next();
        jpaManager.cleanupPluginResources(pluginId, pluginContext.getApplicationContext());
    }

    private void unregisterMybatis() {
        String pluginId = pluginContext.getPluginId();
        AnnotationConfigApplicationContext applicationContext = getApplicationContext();
        GJPluginMybatisSqlSessionManager mybatisRegistry = applicationContext.getBean(GJPluginMybatisSqlSessionManager.class);
        mybatisRegistry.cleanupPluginResources(pluginId);
    }

    private void unregisterEventListeners() {
        var mainCtx = (GenericApplicationContext) pluginContext.getMainApplicationContext();
        if (mainCtx.getBeansOfType(GJPluginLocalEventBus.class).isEmpty()) {
            return;
        }
        GJPluginLocalEventBus eventBus = mainCtx.getBean(GJPluginLocalEventBus.class);
        eventBus.unregisterListeners(pluginContext.getPluginId());
    }

    private void unregisterQuartzJobs() {
        var mainCtx = (GenericApplicationContext) pluginContext.getMainApplicationContext();
        if (mainCtx.getBeansOfType(PluginJobManager.class).isEmpty()) {
            return;
        }
        PluginJobManager jobManager = mainCtx.getBean(PluginJobManager.class);
        jobManager.unregisterJobs(pluginContext.getPluginId());
    }

    private void unregisterI18NMessageSource() {
        String pluginId = pluginContext.getPluginId();
        String il8nPluginBeanName = "plugin_i18n_" + pluginId;
        AnnotationConfigApplicationContext applicationContext = getApplicationContext();
        ConfigurableListableBeanFactory beanFactory = applicationContext.getBeanFactory();
        if (beanFactory.containsBean(il8nPluginBeanName)) {
            beanFactory.destroyBean(il8nPluginBeanName);
        }
    }

    private void unregisterControllers() {
        String pluginId = pluginContext.getPluginId();
        GenericApplicationContext mainAppCtx = (GenericApplicationContext) pluginContext.getMainApplicationContext();

        // WebFlux mode
        Map<String, GJPluginWebFluxRequestMappingHandlerMapping> webFluxMappings =
                mainAppCtx.getBeansOfType(GJPluginWebFluxRequestMappingHandlerMapping.class);
        if (!webFluxMappings.isEmpty()) {
            webFluxMappings.values().iterator().next().unregisterHandlerMethods(pluginId);
        } else {
            // MVC mode (default)
            Map<String, GJPluginRequestMappingHandlerMapping> mvcMappings =
                    mainAppCtx.getBeansOfType(GJPluginRequestMappingHandlerMapping.class);
            if (!mvcMappings.isEmpty()) {
                mvcMappings.values().iterator().next().unregisterController(pluginId);
            }
        }

        // OpenAPI cleanup
        ((AbstractAutowireCapableBeanFactory) mainAppCtx.getBeanFactory())
                .destroySingleton(GJPluginOpenApiConfig.PLUGIN_SWAGGER_BEAN_PREFIX + pluginId);
        GJPluginOpenApiConfig.unregisterPluginOpenApiBeans(pluginId);
        Object resource = GJPluginOpenApiConfig.findMultipleOpenApiResource(mainAppCtx);
        if (resource != null) {
            try {
                Field groupedOpenApisField =
                        GJPluginOpenApiConfig.getGroupedOpenApisField(resource);
                @SuppressWarnings("unchecked")
                List<GroupedOpenApi> groupedOpenApis =
                        (List<GroupedOpenApi>) groupedOpenApisField.get(resource);
                groupedOpenApis.removeIf(g -> g.getGroup().equals(pluginId));
                resource.getClass().getMethod("afterPropertiesSet").invoke(resource);
            } catch (Exception e) {
                log.warn("[Plugin: {}] Failed to remove OpenAPI group from springdoc", pluginId, e);
            }
        }
    }

    private AnnotationConfigApplicationContext getApplicationContext() {
        ApplicationContext context = pluginContext.getApplicationContext();
        return (AnnotationConfigApplicationContext) context;
    }
}
