/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.util.*;

/**
 * Dispatches lifecycle phases to registered {@link PluginResourceRegistrar}s.
 * Built-in registrars let exceptions propagate; external registrars are isolated.
 */
public class PluginLifecycleEngine {

    private static final Logger log = LoggerFactory.getLogger(PluginLifecycleEngine.class);

    private final List<PluginResourceRegistrar> registrars;
    private final Set<PluginResourceRegistrar> externalSet;

    private PluginLifecycleEngine(GenericApplicationContext mainAppCtx,
                                   List<PluginResourceRegistrar> programmaticRegistrars) {

        // External registrars (Spring Bean + programmatic): exceptions are isolated
        List<PluginResourceRegistrar> externals = new ArrayList<>();
        externals.addAll(mainAppCtx.getBeansOfType(PluginResourceRegistrar.class, false, false)
                .values());
        externals.addAll(programmaticRegistrars);

        // Merge built-in and external into a single ordered list
        List<PluginResourceRegistrar> all = new ArrayList<>();
        all.addAll(builtinRegistrars());
        all.addAll(externals);
        this.registrars = List.copyOf(new LinkedHashSet<>(all));
        this.externalSet = Set.copyOf(externals);
    }

    /** Assemble all registrars. External registrars discovered via Spring Bean and programmatic API. */
    public static PluginLifecycleEngine create(
            GenericApplicationContext mainAppCtx,
            List<PluginResourceRegistrar> programmaticRegistrars) {
        return new PluginLifecycleEngine(mainAppCtx, programmaticRegistrars);
    }

    /** Built-in registrars in execution order. */
    private static List<PluginResourceRegistrar> builtinRegistrars() {
        return List.of(
                new ObjectMapperRegistrar(),        // 0
                new PropertyResourceRegistrar(),    // 1
                new I18NRegistrar(),                // 2
                new MybatisRegistrar(),             // 3
                new JpaRegistrar(),                 // 4
                new SqlScriptRegistrar(),           // 5
                new MigrationRegistrar(),           // 6
                new ControllerRegistrar(),          // 10
                new OpenApiRegistrar(),             // 11
                new AnonymousPathRegistrar(),       // 11
                new HubRegistrar(),                 // 11
                new ModelMapperRegistrar(),         // 12
                new EventListenerRegistrar(),       // 13
                new QuartzJobRegistrar(),           // 14
                new TableKeywordRegistrar()         // 15
        );
    }

    public void executePhase(PluginLifecyclePhase phase,
                             AnnotationConfigApplicationContext appCtx) {
        // Guard: null arguments from internal mis-invocation should log, not crash
        if (phase == null || appCtx == null) {
            log.error("[Lifecycle] executePhase called with null argument: phase={}, appCtx={}",
                    phase, appCtx);
            return;
        }
        long startTime = System.currentTimeMillis();
        String pluginId = appCtx.getId();

        Comparator<PluginResourceRegistrar> orderCmp =
                Comparator.comparingInt(PluginResourceRegistrar::order)
                          .thenComparing(r -> r.getClass().getName());

        if (phase == PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE) {
            orderCmp = orderCmp.reversed();
        }

        // Merge and sort by order; dispatch built-in (exceptions propagate) vs external (isolated)
        List<PluginResourceRegistrar> sorted = registrars.stream()
                .filter(r -> r.phases().contains(phase))
                .sorted(orderCmp)
                .toList();

        int total = 0;
        List<String> names = new ArrayList<>();
        for (PluginResourceRegistrar r : sorted) {
            if (externalSet.contains(r)) {
                String name = dispatchExternal(r, phase, appCtx);
                if (name != null) {
                    names.add(name);
                    total++;
                }
            } else {
                dispatchBuiltin(r, phase, appCtx);
                names.add(r.getClass().getSimpleName());
                total++;
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("[Lifecycle] Phase {} for plugin '{}': {} registrars executed ({} ms) — {}",
                phase, pluginId, total, duration, String.join(", ", names));
    }

    private void dispatchBuiltin(PluginResourceRegistrar r,
                                  PluginLifecyclePhase phase,
                                  AnnotationConfigApplicationContext appCtx) {
        switch (phase) {
            case BEFORE_CONTEXT_REFRESH -> r.onBeforeContextRefresh(appCtx);
            case AFTER_CONTEXT_REFRESH  -> r.onAfterContextRefresh(appCtx);
            case BEFORE_CONTEXT_CLOSE   -> r.onBeforeContextClose(appCtx);
        }
        // Exceptions from built-in registrars propagate uncaught
    }

    private String dispatchExternal(PluginResourceRegistrar r,
                                     PluginLifecyclePhase phase,
                                     AnnotationConfigApplicationContext appCtx) {
        try {
            switch (phase) {
                case BEFORE_CONTEXT_REFRESH -> r.onBeforeContextRefresh(appCtx);
                case AFTER_CONTEXT_REFRESH  -> r.onAfterContextRefresh(appCtx);
                case BEFORE_CONTEXT_CLOSE   -> r.onBeforeContextClose(appCtx);
            }
            return r.getClass().getSimpleName();
        } catch (Exception e) {
            log.error("[Lifecycle] Registrar {} FAILED at phase {} for plugin '{}': {}",
                    r.getClass().getSimpleName(), phase, appCtx.getId(),
                    e.getMessage(), e);
            return null;
        }
    }
}
