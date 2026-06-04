/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.eventbus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gj.pf4j.GJJackson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.util.AntPathMatcher;

import javax.annotation.PreDestroy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class GJPluginLocalEventBus {

    private static final Logger log = LoggerFactory.getLogger(GJPluginLocalEventBus.class);

    private final ObjectMapper objectMapper = GJJackson.INSTANCE;
    private final AntPathMatcher pathMatcher = new AntPathMatcher(".");
    private final Executor asyncExecutor;

    /** eventPattern → List&lt;ListenerInfo&gt; */
    private final Map<String, List<ListenerInfo>> registry = new ConcurrentHashMap<>();

    /** listenerClass → handleMethod */
    private final Map<Class<?>, Method> methodCache = new ConcurrentHashMap<>();

    /** pluginId → registered listener instances, for unregister reference matching */
    private final Map<String, List<GJPluginLocalEventListener<?>>> pluginListeners = new ConcurrentHashMap<>();

    private final Object registryLock = new Object();

    public GJPluginLocalEventBus() {
        this(new ThreadPoolExecutor(
                0, 1000,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new ThreadPoolExecutor.CallerRunsPolicy()));
    }

    public GJPluginLocalEventBus(Executor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    // ==================== register / unregister ====================

    /**
     * Scan the plugin Spring context for all {@link GJPluginLocalEventListener} beans
     * and register them with the event bus.
     */
    public void registerListeners(String pluginId, ApplicationContext pluginCtx) {
        Map<String, GJPluginLocalEventListener> beans =
                pluginCtx.getBeansOfType(GJPluginLocalEventListener.class);
        if (beans.isEmpty()) {
            log.debug("[Plugin: {}] No event listeners found, skipping registration", pluginId);
            return;
        }

        List<GJPluginLocalEventListener<?>> listeners = new ArrayList<>();
        for (GJPluginLocalEventListener<?> listener : beans.values()) {
            registerListener(listener);
            listeners.add(listener);
        }
        pluginListeners.put(pluginId, listeners);
        log.info("[Plugin: {}] Registered {} event listener(s)", pluginId, listeners.size());
    }

    /**
     * Unregister all event listeners belonging to the given plugin.
     */
    public void unregisterListeners(String pluginId) {
        List<GJPluginLocalEventListener<?>> listeners = pluginListeners.remove(pluginId);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (GJPluginLocalEventListener<?> listener : listeners) {
            unregisterListener(listener);
        }
        log.info("[Plugin: {}] Unregistered {} event listener(s)", pluginId, listeners.size());
    }

    // ==================== publish ====================

    /**
     * Publish an event synchronously.
     */
    public <T> void publish(T event) {
        String eventName = getEventNameFromAnnotation(event.getClass());
        doPublish(eventName, event, false);
    }

    /**
     * Publish an event asynchronously.
     */
    public <T> void publishAsync(T event) {
        String eventName = getEventNameFromAnnotation(event.getClass());
        doPublish(eventName, event, true);
    }

    // ==================== internal ====================

    private void registerListener(GJPluginLocalEventListener<?> listener) {
        Objects.requireNonNull(listener, "Listener cannot be null");

        Class<?> listenerClass = listener.getClass();
        Class<?> eventType = getEventType(listenerClass);
        String eventPattern = getEventNameFromAnnotation(eventType);

        getHandleMethod(listenerClass, eventType);

        synchronized (registryLock) {
            registry.computeIfAbsent(eventPattern, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(new ListenerInfo(listener, eventType, listenerClass));
        }
    }

    private void unregisterListener(GJPluginLocalEventListener<?> listener) {
        synchronized (registryLock) {
            registry.values().forEach(list -> list.removeIf(info -> info.listener == listener));
            registry.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
        methodCache.remove(listener.getClass());
    }

    private <T> void doPublish(String eventName, T event, boolean async) {
        log.debug("Publishing event [{}] (async={})", eventName, async);

        String jsonPayload = serializeEvent(event);

        List<ListenerInfo> matchedListeners = registry.entrySet().stream()
                .filter(entry -> pathMatcher.match(entry.getKey(), eventName))
                .flatMap(entry -> entry.getValue().stream())
                .toList();

        if (matchedListeners.isEmpty()) {
            log.debug("No listeners matched for event [{}]", eventName);
            return;
        }

        log.debug("Event [{}] matched {} listener(s)", eventName, matchedListeners.size());

        for (ListenerInfo info : matchedListeners) {
            Runnable task = () -> invokeListener(info, jsonPayload);
            if (async) {
                try {
                    asyncExecutor.execute(task);
                } catch (RejectedExecutionException e) {
                    log.error("Async event execution rejected for [{}], listener [{}]. "
                            + "Running in caller thread as fallback.", eventName,
                            info.listenerClass.getName(), e);
                    task.run();
                }
            } else {
                task.run();
            }
        }
    }

    @PreDestroy
    private void shutdown() {
        if (asyncExecutor instanceof java.util.concurrent.ExecutorService) {
            ((java.util.concurrent.ExecutorService) asyncExecutor).shutdown();
        }
    }

    private String serializeEvent(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event: " + event.getClass(), e);
        }
    }

    private String getEventNameFromAnnotation(Class<?> eventClass) {
        EventName anno = eventClass.getAnnotation(EventName.class);
        if (anno == null) {
            log.error("Event class must be annotated with @EventName: {}", eventClass.getName());
            throw new IllegalArgumentException(
                    "Event class must be annotated with @EventName: " + eventClass.getName()
            );
        }
        return anno.value();
    }

    @SuppressWarnings("unchecked")
    private Class<?> getEventType(Class<?> listenerClass) {
        Type[] genericInterfaces = listenerClass.getGenericInterfaces();
        for (Type generic : genericInterfaces) {
            if (generic instanceof ParameterizedType paramType) {
                if (paramType.getRawType() == GJPluginLocalEventListener.class) {
                    Type actualType = paramType.getActualTypeArguments()[0];
                    if (actualType instanceof Class) {
                        return (Class<?>) actualType;
                    }
                }
            }
        }
        throw new IllegalArgumentException(
                "Cannot determine event type for listener: " + listenerClass.getName() +
                        ". Ensure it implements GJPluginLocalEventListener<T> with concrete type."
        );
    }

    private Method getHandleMethod(Class<?> listenerClass, Class<?> eventType) {
        return methodCache.computeIfAbsent(listenerClass, cls -> {
            try {
                return cls.getMethod("HandleEvent", eventType);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                        "Listener class must implement 'HandleEvent(" + eventType.getSimpleName()
                                + ")' method: " + cls.getName(), e
                );
            }
        });
    }

    private void invokeListener(ListenerInfo info, String jsonPayload) {
        long start = System.nanoTime();
        try {
            Object eventObj = objectMapper.readValue(jsonPayload, info.eventType);
            Method handleMethod = getHandleMethod(info.listenerClass, info.eventType);
            handleMethod.invoke(info.listener, eventObj);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Error invoking event listener: {}", info.listenerClass.getName(), cause);
        } catch (Exception e) {
            log.error("Error invoking event listener: {}", info.listenerClass.getName(), e);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.debug("Event listener completed: {} took {}ms", info.listenerClass.getName(), durationMs);
        }
    }

    // ==================== internal types ====================

    private static class ListenerInfo {
        final GJPluginLocalEventListener<?> listener;
        final Class<?> eventType;
        final Class<?> listenerClass;

        ListenerInfo(GJPluginLocalEventListener<?> listener, Class<?> eventType, Class<?> listenerClass) {
            this.listener = listener;
            this.eventType = eventType;
            this.listenerClass = listenerClass;
        }
    }
}
