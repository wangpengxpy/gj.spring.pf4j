/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.anonymous.AnonymousPathEntry;
import gj.pf4j.anonymous.AnonymousRouteDeclaration;
import gj.pf4j.anonymous.PluginAnonymousPathRegistrar;
import gj.pf4j.webflux.GJRouterFunctions;
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
                        log.warn("[Plugin: {}] Anonymous functional route {}:{} has blank reason — " +
                                "registration as anonymous is blocked. " +
                                "A non-empty reason is required for audit.",
                                pluginCtx.getId(), decl.httpMethod(), decl.pathPattern());
                        continue;
                    }
                    registrar.register(pluginCtx.getId(),
                            new AnonymousPathEntry(
                                    pluginCtx.getId(), decl.pathPattern(),
                                    decl.httpMethod(), null, null, decl.reason(),
                                    java.time.LocalDateTime.now()));
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
