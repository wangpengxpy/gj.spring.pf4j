/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.security;

import gj.pf4j.GJPluginFilterPosition;
import gj.pf4j.GJPluginFilterRegistry;
import gj.pf4j.lifecycle.PluginLifecyclePhase;
import gj.pf4j.lifecycle.PluginResourceRegistrar;
import gj.pf4j.security.reactive.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Scans plugin WebFlux filter extensions and registers them into
 * {@link GJPluginFilterRegistry}, subject to host configuration.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class ReactiveFilterRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(ReactiveFilterRegistrar.class);

    private final GJPluginFilterRegistry registry;
    private final PluginFilterConfigProperties config;

    public ReactiveFilterRegistrar(GJPluginFilterRegistry registry,
                                    PluginFilterConfigProperties config) {
        this.registry = registry;
        this.config = config;
    }

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH,
                      PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() { return 910; }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
        if (!config.getFilter().isEnabled()) return;

        String pluginId = pluginCtx.getId();
        Set<GJPluginFilterPosition> allowed = config.getFilter().getAllowedPositions();
        PluginFilterConfigProperties.PluginSecurityConfig pluginConfig =
                config.getPlugins().get(pluginId);
        Set<GJPluginFilterPosition> pluginPositions = pluginConfig != null &&
                pluginConfig.getFilter() != null
                ? pluginConfig.getFilter().getAllowedPositions()
                : allowed;

        register(pluginCtx, pluginId, FirstWebFilterExtension.class, GJPluginFilterPosition.FIRST,
                allowed, pluginPositions);
        register(pluginCtx, pluginId, SessionRestoreWebFilterExtension.class,
                GJPluginFilterPosition.SESSION_RESTORE, allowed, pluginPositions);
        register(pluginCtx, pluginId, FormLoginWebFilterExtension.class,
                GJPluginFilterPosition.FORM_LOGIN, allowed, pluginPositions);
        register(pluginCtx, pluginId, AnonymousWebFilterExtension.class,
                GJPluginFilterPosition.ANONYMOUS, allowed, pluginPositions);
        register(pluginCtx, pluginId, PreAuthorizeWebFilterExtension.class,
                GJPluginFilterPosition.PRE_AUTHORIZE, allowed, pluginPositions);
        register(pluginCtx, pluginId, LastWebFilterExtension.class, GJPluginFilterPosition.LAST,
                allowed, pluginPositions);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void register(AnnotationConfigApplicationContext pluginCtx, String pluginId,
                          Class extensionClass, GJPluginFilterPosition pos,
                          Set<GJPluginFilterPosition> allowed,
                          Set<GJPluginFilterPosition> pluginPositions) {
        if (!allowed.contains(pos) || !pluginPositions.contains(pos)) {
            return;
        }
        var beans = pluginCtx.getBeansOfType(extensionClass);
        for (Object bean : beans.values()) {
            int order = 0;
            try {
                order = (int) bean.getClass().getMethod("getOrder").invoke(bean);
            } catch (Exception ignored) {}
            try {
                var filter = bean.getClass().getMethod("getWebFilter").invoke(bean);
                registry.registerWebFilter(pluginId, pos,
                        (org.springframework.web.server.WebFilter) filter, order);
                log.info("[Plugin: {}] Registered {} web filter at {} (order={})",
                        pluginId, bean.getClass().getSimpleName(), pos, order);
            } catch (Exception e) {
                log.error("[Plugin: {}] Failed to get web filter from {}: {}",
                        pluginId, bean.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginCtx) {
        registry.unregister(pluginCtx.getId());
    }
}
