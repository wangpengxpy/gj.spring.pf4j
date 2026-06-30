/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.i18n.GJI18nProperties;
import gj.pf4j.i18n.GJPluginReloadableMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.Set;

class I18NRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(I18NRegistrar.class);

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() { return 2; }

    @Override
    public void onBeforeContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
        String pluginId = pluginCtx.getId();
        long startTime = System.currentTimeMillis();
        log.debug("[i18n] Starting registration for plugin: '{}'", pluginId);
        try {
            String i18nPluginBeanName = "plugin_i18n_" + pluginId;
            ConfigurableListableBeanFactory beanFactory = pluginCtx.getBeanFactory();
            if (beanFactory.containsBean(i18nPluginBeanName)) {
                beanFactory.destroyBean(i18nPluginBeanName);
            }
            ApplicationContext hostCtx = pluginCtx.getParent();
            ReloadableResourceBundleMessageSource mainMs =
                    hostCtx.getBean("messageSource", ReloadableResourceBundleMessageSource.class);
            GJPluginReloadableMessageSource pluginMs = new GJPluginReloadableMessageSource(
                    "i18n/messages",
                    pluginCtx.getClassLoader(),
                    mainMs,
                    hostCtx.getBean(GJI18nProperties.class)
            );
            beanFactory.registerSingleton(i18nPluginBeanName, pluginMs);
            log.debug("[i18n] Registered message source bean: '{}' for plugin: '{}'",
                    i18nPluginBeanName, pluginId);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[i18n] Successfully registered resources for plugin: '{}' (took {} ms)",
                    pluginId, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[i18n] Failed to register resources for plugin: '{}' (took {} ms). " +
                            "Error: {}",
                    pluginId, duration, e.getMessage(), e);
            throw new RuntimeException("i18n registration failed for plugin: " + pluginId, e);
        }
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginCtx) {
        String pluginId = pluginCtx.getId();
        String i18nPluginBeanName = "plugin_i18n_" + pluginId;
        ConfigurableListableBeanFactory beanFactory = pluginCtx.getBeanFactory();
        if (beanFactory.containsBean(i18nPluginBeanName)) {
            beanFactory.destroyBean(i18nPluginBeanName);
        }
    }
}
