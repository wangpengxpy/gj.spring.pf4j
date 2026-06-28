/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.GJPluginContext;
import gj.pf4j.i18n.GJI18nProperties;
import gj.pf4j.i18n.GJPluginReloadableMessageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.util.Set;

class I18NRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(I18NRegistrar.class);

    private final GJPluginContext pluginContext;
    private final GenericApplicationContext mainAppCtx;

    I18NRegistrar(GJPluginContext pluginContext, GenericApplicationContext mainAppCtx) {
        this.pluginContext = pluginContext;
        this.mainAppCtx = mainAppCtx;
    }

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.BEFORE_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() { return 2; }

    @Override
    public void onBeforeContextRefresh(AnnotationConfigApplicationContext ctx) {
        String pluginId = pluginContext.getPluginId();
        long startTime = System.currentTimeMillis();
        log.debug("[il8n] Starting registration for plugin: '{}'", pluginId);
        try {
            String il8nPluginBeanName = "plugin_i18n_" + pluginId;
            ConfigurableListableBeanFactory beanFactory = ctx.getBeanFactory();
            if (beanFactory.containsBean(il8nPluginBeanName)) {
                beanFactory.destroyBean(il8nPluginBeanName);
            }
            ReloadableResourceBundleMessageSource mainMs =
                    this.mainAppCtx.getBean("messageSource", ReloadableResourceBundleMessageSource.class);
            GJPluginReloadableMessageSource pluginMs = new GJPluginReloadableMessageSource(
                    "i18n/messages",
                    pluginContext.getClassLoader(),
                    mainMs,
                    this.mainAppCtx.getBean(GJI18nProperties.class)
            );
            beanFactory.registerSingleton(il8nPluginBeanName, pluginMs);
            log.debug("[il8n] Registered message source bean: '{}' for plugin: '{}'",
                    il8nPluginBeanName, pluginId);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[il8n] Successfully registered resources for plugin: '{}' (took {} ms)",
                    pluginId, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[il8n] Failed to register resources for plugin: '{}' (took {} ms). " +
                            "Error: {}",
                    pluginId, duration, e.getMessage(), e);
            throw new RuntimeException("il8n registration failed for plugin: " + pluginId, e);
        }
    }

    @Override
    public void onBeforeContextClose() {
        String pluginId = pluginContext.getPluginId();
        String il8nPluginBeanName = "plugin_i18n_" + pluginId;
        AnnotationConfigApplicationContext ctx = (AnnotationConfigApplicationContext)
                pluginContext.getApplicationContext();
        ConfigurableListableBeanFactory beanFactory = ctx.getBeanFactory();
        if (beanFactory.containsBean(il8nPluginBeanName)) {
            beanFactory.destroyBean(il8nPluginBeanName);
        }
    }
}
