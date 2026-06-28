/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.lifecycle;

import gj.pf4j.GJPluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Comparator;
import java.util.List;

/** Dispatches lifecycle phases to registered {@link PluginResourceRegistrar}s. */
public class PluginLifecycleEngine {

    private static final Logger log = LoggerFactory.getLogger(PluginLifecycleEngine.class);

    private final List<PluginResourceRegistrar> registrars;

    public PluginLifecycleEngine(List<PluginResourceRegistrar> registrars) {
        this.registrars = List.copyOf(registrars);
    }

    /**
     * Assemble all registrars. Execution order is determined solely by
     * {@link PluginResourceRegistrar#order()}; the list here is written
     * in that sequence for readability. When adding a new registrar,
     * append to the end and set its {@code order()} to the desired position.
     */
    public static PluginLifecycleEngine create(
            GJPluginContext pluginContext,
            GenericApplicationContext mainAppCtx) {
        // listed in order() sequence; executePhase sorts by order() at runtime
        return new PluginLifecycleEngine(List.of(
                new ObjectMapperRegistrar(pluginContext, mainAppCtx),        // 0
                new PropertyResourceRegistrar(pluginContext),               // 1
                new I18NRegistrar(pluginContext, mainAppCtx),               // 2
                new MybatisRegistrar(pluginContext, mainAppCtx),            // 3
                new JpaRegistrar(pluginContext, mainAppCtx),                // 4
                new MigrationRegistrar(pluginContext, mainAppCtx),          // 5
                new ControllerRegistrar(pluginContext, mainAppCtx),         // 10
                new HubRegistrar(pluginContext, mainAppCtx),                // 11
                new ModelMapperRegistrar(pluginContext),                    // 12
                new EventListenerRegistrar(pluginContext, mainAppCtx),      // 13
                new QuartzJobRegistrar(pluginContext, mainAppCtx),          // 14
                new TableKeywordRegistrar(pluginContext, mainAppCtx)        // 15
        ));
    }

    public void executePhase(PluginLifecyclePhase phase,
                             AnnotationConfigApplicationContext ctx) {
        long startTime = System.currentTimeMillis();
        Comparator<PluginResourceRegistrar> orderCmp =
                Comparator.comparingInt(PluginResourceRegistrar::order);
        if (phase == PluginLifecyclePhase.BEFORE_CONTEXT_CLOSE) {
            orderCmp = orderCmp.reversed();
        }
        List<String> names = registrars.stream()
                .filter(r -> r.phases().contains(phase))
                .sorted(orderCmp)
                .map(r -> {
                    log.debug("[Lifecycle] Executing phase {} on registrar {} (order={})",
                            phase, r.getClass().getSimpleName(), r.order());
                    dispatch(r, phase, ctx);
                    return r.getClass().getSimpleName();
                })
                .toList();
        long duration = System.currentTimeMillis() - startTime;
        if (names.isEmpty()) {
            log.info("[Lifecycle] Phase {}: no registrars matched ({} ms)", phase, duration);
        } else {
            log.info("[Lifecycle] Phase {}: [{}] ({} ms)", phase,
                    String.join(", ", names), duration);
        }
    }

    private void dispatch(PluginResourceRegistrar r,
                          PluginLifecyclePhase phase,
                          AnnotationConfigApplicationContext ctx) {
        switch (phase) {
            case BEFORE_CONTEXT_REFRESH -> r.onBeforeContextRefresh(ctx);
            case AFTER_CONTEXT_REFRESH  -> r.onAfterContextRefresh(ctx);
            case BEFORE_CONTEXT_CLOSE   -> r.onBeforeContextClose();
        }
    }
}
