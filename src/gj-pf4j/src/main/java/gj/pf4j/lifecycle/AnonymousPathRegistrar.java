/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.core.AnonymousPathEntry;
import gj.pf4j.core.AnonymousRouteDeclaration;
import gj.pf4j.core.PluginAnonymousPathRegistrar;
import gj.pf4j.webflux.GJRouterFunctions;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.reactive.function.server.RouterFunction;

import java.util.Map;
import java.util.Set;

class AnonymousPathRegistrar implements PluginResourceRegistrar {

    private static final Logger log = LoggerFactory.getLogger(AnonymousPathRegistrar.class);

    @Override
    public Set<PluginLifecyclePhase> phases() {
        return Set.of(PluginLifecyclePhase.AFTER_CONTEXT_REFRESH,
                PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE);
    }

    @Override
    public int order() { return 11; }

    @Override
    public void onAfterContextRefresh(AnnotationConfigApplicationContext pluginCtx) {
        ApplicationContext hostCtx = pluginCtx.getParent();
        PluginAnonymousPathRegistrar registrar =
                hostCtx.getBeanProvider(PluginAnonymousPathRegistrar.class).getIfAvailable();
        if (registrar == null) return;

        Map<String, RouterFunction> routerFunctions =
                pluginCtx.getBeansOfType(RouterFunction.class);
        for (RouterFunction rf : routerFunctions.values()) {
            if (rf instanceof GJRouterFunctions.AnnotatedRouterFunction arf) {
                for (AnonymousRouteDeclaration decl : arf.getDeclarations()) {
                    if (decl.reason() == null || decl.reason().isBlank()) {
                        log.debug("[Plugin: {}] Skipping non-anonymous functional route {}:{}",
                                pluginCtx.getId(), decl.httpMethod(), decl.pathPattern());
                        continue;
                    }
                    registrar.register(pluginCtx.getId(),
                            new AnonymousPathEntry(
                                    pluginCtx.getId(), decl.pathPattern(),
                                    decl.httpMethod(), null, null, decl.reason(),
                                    LocalDateTime.now()));
                }
            }
        }
    }

    @Override
    public void onBeforeContextClose(AnnotationConfigApplicationContext pluginCtx) {
        ApplicationContext hostCtx = pluginCtx.getParent();
        PluginAnonymousPathRegistrar registrar =
                hostCtx.getBeanProvider(PluginAnonymousPathRegistrar.class).getIfAvailable();
        if (registrar != null) {
            registrar.unregister(pluginCtx.getId());
        }
    }
}
