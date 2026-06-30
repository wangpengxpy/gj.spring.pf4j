/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.mybatis.interceptor.GJTableKeywordProvider;
import gj.pf4j.mybatis.interceptor.GJTableKeywordRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

class TableKeywordRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(TableKeywordRegistrar.class);

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH);
    }

    @Override
    public int order() { return 15; }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
        Map<String, GJTableKeywordProvider> providers =
                pluginCtx.getBeansOfType(GJTableKeywordProvider.class);
        if (providers.isEmpty()) {
            return;
        }
        if (pluginCtx.getParent().getBeansOfType(GJTableKeywordRegistry.class).isEmpty()) {
            log.debug("[Plugin: {}] GJTableKeywordRegistry not registered, " +
                    "skipping keyword registration", pluginCtx.getId());
            return;
        }
        GJTableKeywordRegistry registry = pluginCtx.getParent().getBean(GJTableKeywordRegistry.class);
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
                    pluginCtx.getId(), merged.size());
        }
    }
}
