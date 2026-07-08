/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.listener.DataListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public abstract class GJHub implements GJSocketIOHub {
    protected final Logger log = LoggerFactory.getLogger(GJHub.class);

    // ================ Core Fields ================
    private final String hubName;

    // HubManager reference (injected by GJHubManager.registerHub for unified group management)
    private GJHubManager hubManager;

    // Invocation method cache
    private final Map<String, DataListener<Object>> methodHandlers = new ConcurrentHashMap<>();

    // Connection management
    protected final Map<String, GJHubConnectionContext> connections = new ConcurrentHashMap<>();
    protected final Map<String, Set<String>> userConnections = new ConcurrentHashMap<>();

    // Thread management
    protected ThreadFactory threadFactory;
    protected ExecutorService asyncExecutor;
    protected ScheduledExecutorService heartbeatScheduler;

    // Thread-local variables for storing current request context
    private final ThreadLocal<GJHubConnectionContext> currentContext = new ThreadLocal<>();
    private final ThreadLocal<GJHubCallerClients> currentClients = new ThreadLocal<>();
    private final ThreadLocal<GJGroupManager> currentGroups = new ThreadLocal<>();

    // Monitoring statistics
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong totalMessages = new AtomicLong(0);

    // ================ Constructor ================

    protected GJHub(String hubName) {
        if (hubName == null || hubName.isEmpty()) {
            throw new IllegalArgumentException("hubName is null or empty");
        }
        hubName = hubName.toLowerCase();
        this.hubName = hubName;
    }

    @PostConstruct
    public void initialize() {
        registerAnnotatedMethods(this);
        this.threadFactory = new GJSocketIOThreadFactory.Builder()
                .setNameFormat("hub-" + hubName + "-%d")
                .setDaemon(true)
                .setUncaughtExceptionHandler((thread, throwable) -> {
                    log.error("Uncaught exception in thread {}: {}", thread.getName(), throwable.getMessage(), throwable);
                })
                .build();
        this.asyncExecutor = createExecutor();
        this.heartbeatScheduler = Executors.newSingleThreadScheduledExecutor(
                new GJSocketIOThreadFactory.Builder()
                        .setNameFormat("hub-" + hubName + "-heartbeat-%d")
                        .setDaemon(true)
                        .build()
        );
        log.info("Hub initialized: {}", hubName);
    }

    // ================ Abstract Methods (optional implementation by subclasses) ================

    public abstract CompletableFuture<Void> onConnectedAsync();

    public abstract CompletableFuture<Void> onDisconnectedAsync();

    private void registerMethod(String methodName, DataListener<Object> listener) {
        methodHandlers.put(methodName, listener);
    }

    private void registerAnnotatedMethods(Object target) {
        long startTime = System.nanoTime();
        int methodCount = 0;
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(GJHubMethod.class)) {
                GJHubMethod anno = method.getAnnotation(GJHubMethod.class);
                if (anno == null) {
                    continue;
                }
                String methodName = anno.value();
                if (methodName == null || methodName.isEmpty()) {
                    log.warn("methodName is null or empty for hub: {}, skipping method: {}", hubName, method.getName());
                    continue;
                }
                methodName = methodName.toLowerCase();
                String finalMethodName = methodName;
                DataListener<Object> listener = (client, data, ack) -> {
                    try {
                        method.setAccessible(true);
                        method.invoke(target, data);
                    } catch (Exception e) {
                        throw new RuntimeException("Error invoking hub (" + getHubName() + ") method：" + finalMethodName, e);
                    }
                };
                registerMethod(methodName, listener);
                methodCount++;
            }
        }
        long durationNanos = System.nanoTime() - startTime;
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(durationNanos);
        log.info("Hub '{}' registered {} @GJHubMethod(s) in {} ms", hubName, methodCount, durationMillis);
    }

    private DataListener<Object> getHandler(String methodName) {
        return methodHandlers.get(methodName);
    }

    // ================ SocketIOHub Interface Implementation ================
    @Override
    public String getHubName() {
        return hubName;
    }

    void setHubManager(GJHubManager hubManager) {
        this.hubManager = hubManager;
    }

    CompletableFuture<Void> onClientConnectedAsync(SocketIOClient client, String userId) {
        String connectionId = client.getSessionId().toString();

        return CompletableFuture.runAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                // 1. Create connection context
                Map<String, String> queryParams = extractQueryParams(client);
                GJHubConnectionContext context = new GJHubConnectionContext(
                        connectionId, userId, client, queryParams
                );
                // 2. Store connection
                connections.put(connectionId, context);
                // 3. Store user connection mapping
                userConnections.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                        .add(connectionId);
                // 4. Set current thread context
                setCurrentContext(context);
                // 5. Call subclass connection handler
                CompletableFuture<Void> future = onConnectedAsync();
                CompletableFuture<Void> safeFuture = future != null ? future : CompletableFuture.completedFuture(null);
                // Block until plugin's onConnectedAsync completes — required to keep
                // ThreadLocal context alive and ensure stats/log reflect final state.
                safeFuture.get();
                // 6. Update statistics
                totalConnections.incrementAndGet();
                activeConnections.incrementAndGet();
                long cost = System.currentTimeMillis() - startTime;
                log.info("Client {} connected to {} in {}ms (User: {})",
                        connectionId, hubName, cost, userId);
            } catch (Exception e) {
                log.error("Failed to handle connection for client {}: {}", connectionId, e.getMessage(), e);
                throw new RuntimeException("Connection handling failed", e);
            } finally {
                clearCurrentContext();
            }
        }, asyncExecutor);
    }

    CompletableFuture<Void> onClientDisconnectedAsync(SocketIOClient client) {
        String connectionId = client.getSessionId().toString();
        return CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            GJHubConnectionContext context = connections.get(connectionId);
            if (context == null) {
                log.warn("No context found for disconnected client: {}", connectionId);
                return;
            }
            try {
                setCurrentContext(context);
                CompletableFuture<Void> future = onDisconnectedAsync();
                CompletableFuture<Void> safeFuture = future != null ? future : CompletableFuture.completedFuture(null);
                // Block until plugin's onDisconnectedAsync completes — required to keep
                // ThreadLocal context alive and ensure stats/log reflect final state.
                safeFuture.get();
                log.info("Client {} disconnected from {} in {}ms (User: {})",
                        connectionId, hubName, System.currentTimeMillis() - startTime, context.getUserId());
            } catch (Exception e) {
                log.error("Failed to handle disconnection for client {}: {}", connectionId, e.getMessage(), e);
            } finally {
                cleanupConnection(connectionId, context.getUserId());
                activeConnections.decrementAndGet();
                clearCurrentContext();
            }
        }, asyncExecutor);
    }

    void onClientMessage(SocketIOClient client, String method, Object data, AckRequest ack) {
        String connectionId = client.getSessionId().toString();
        totalMessages.incrementAndGet();
        GJHubConnectionContext context = connections.get(connectionId);
        if (context == null) {
            log.warn("Received message from unknown client: {}", connectionId);
            return;
        }
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        try {
            // 1. Update last activity time
            context.updateLastActivity();
            // 2. Set current thread context
            setCurrentContext(context);
            // 3. Call subclass method handler
            DataListener<Object> handler = getHandler(method);
            if (handler == null) {
                log.warn("Hub method not found: hub={}, method={}", getHubName(), method);
                return;
            }
            handler.onData(client, data, ack);
            stopWatch.stop();
            log.debug("Invoked hub method: hub={}, method={}, duration={}ms",
                    getHubName(), method, stopWatch.getTotalTimeMillis());
        } catch (Exception e) {
            stopWatch.stop();
            log.error("Error invoking hub method: hub={}, method={}, duration={}ms, error={}",
                    getHubName(), method, stopWatch.getTotalTimeMillis(), e.getMessage(), e);
            // Send error message back to client
            if (client.isChannelOpen()) {
                try {
                    Map<String, Object> error = new HashMap<>();
                    error.put("success", false);
                    error.put("error", "failed to send error");
                    error.put("type", 8);
                    client.sendEvent(method, error);
                } catch (Exception sendError) {
                    log.error("Failed to send error to client: {} hub={}, method={}", connectionId, getHubName(), method, sendError);
                }
            }
            if (ack.isAckRequested()) {
                ack.sendAckData("error: " + e.getMessage());
            }
        } finally {
            clearCurrentContext();
        }
    }

    int getConnectedClientsCount() {
        return connections.size();
    }

    // ================ Context Management ================

    /**
     * Set current thread context
     */
    private void setCurrentContext(GJHubConnectionContext context) {
        currentContext.set(context);
        // Create client proxy (inject hubManager for unified group management)
        GJHubCallerClients clients = new InternalHubClients(
                context.getConnectionId(),
                context.getUserId(),
                context.getClient(),
                hubName,
                hubManager,
                asyncExecutor
        );
        currentClients.set(clients);
        // Create group manager (inject hubManager for unified group management)
        GJGroupManager groups = new InternalGroupManager(
                context.getConnectionId(),
                context.getClient(),
                hubName,
                hubManager,
                asyncExecutor
        );
        currentGroups.set(groups);
    }

    /**
     * Clear current thread context
     */
    private void clearCurrentContext() {
        currentContext.remove();
        currentClients.remove();
        currentGroups.remove();
    }

    /**
     * Get current connection context
     */
    protected GJHubCallerContext getContext() {
        GJHubConnectionContext context = currentContext.get();
        if (context == null) {
            throw new IllegalStateException("No current context available. This method can only be called during connection or message handling.");
        }
        return context.getHubCallerContext();
    }

    /**
     * Get client proxy
     */
    protected GJHubCallerClients getClients() {
        GJHubCallerClients clients = currentClients.get();
        if (clients == null) {
            throw new IllegalStateException("No clients context available. This method can only be called during connection or message handling.");
        }
        return clients;
    }

    /**
     * Get group manager
     */
    protected GJGroupManager getGroups() {
        GJGroupManager groups = currentGroups.get();
        if (groups == null) {
            throw new IllegalStateException("No groups context available. This method can only be called during connection or message handling.");
        }
        return groups;
    }

    // ================ Connection Management ================

    /**
     * Clean up connection
     */
    private void cleanupConnection(String connectionId, String userId) {
        // 1. Remove from connection map
        connections.remove(connectionId);
        // 2. Remove from user connection map
        if (userId != null) {
            Set<String> users = userConnections.get(userId);
            if (users != null) {
                users.remove(connectionId);
                if (users.isEmpty()) {
                    userConnections.remove(userId);
                }
            }
        }
        // 3. Remove from all groups (delegated to global HubManager)
        if (hubManager != null) {
            hubManager.removeFromAllGroups(connectionId);
        }
    }

    /**
     * Get all connections for the specified user
     */
    List<SocketIOClient> getUserClients(String userId) {
        Set<String> connectionIds = userConnections.get(userId);
        if (connectionIds == null || connectionIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SocketIOClient> clients = new ArrayList<>();
        for (String connectionId : connectionIds) {
            GJHubConnectionContext context = connections.get(connectionId);
            if (context != null) {
                clients.add(context.getClient());
            }
        }
        return clients;
    }

    /**
     * Check if connection is active
     */
    boolean isConnectionActive(String connectionId) {
        GJHubConnectionContext context = connections.get(connectionId);
        return context != null && context.getClient().isChannelOpen();
    }

    // ================ Group Management (delegated to global HubManager) ================

    /**
     * Check if connection is in a group
     */
    boolean isInGroupInternal(String connectionId, String groupName) {
        if (hubManager != null) {
            Set<String> members = hubManager.getConnectionIdsInGroup(groupName);
            return members.contains(connectionId);
        }
        return false;
    }

    CompletableFuture<Set<String>> getGroupsForConnectionAsync(String connectionId) {
        if (hubManager != null) {
            return hubManager.getGroupsForConnectionAsync(connectionId);
        }
        return CompletableFuture.completedFuture(Collections.emptySet());
    }

    // ================ Utility Methods ================

    private ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(threadFactory);
    }

    private Map<String, String> extractQueryParams(SocketIOClient client) {
        Map<String, String> params = new HashMap<>();
        client.getHandshakeData().getUrlParams().forEach((key, values) -> {
            if (!values.isEmpty()) {
                params.put(key, values.get(0));
            }
        });
        return params;
    }

    // ================ Statistics ================

    /**
     * Get Hub statistics
     */
    Map<String, Object> getStatistics() {
        return Map.of(
                "hubName", hubName,
                "totalConnections", totalConnections.get(),
                "activeConnections", activeConnections.get(),
                "totalMessages", totalMessages.get(),
                "usersCount", userConnections.size(),
                "groupsCount", hubManager != null ? hubManager.getGroupConnectionsCount() : 0,
                "threadPoolActive", ((ThreadPoolExecutor) asyncExecutor).getActiveCount(),
                "threadPoolQueue", ((ThreadPoolExecutor) asyncExecutor).getQueue().size()
        );
    }

    /**
     * Get active users list
     */
    Set<String> getActiveUsers() {
        return Collections.unmodifiableSet(userConnections.keySet());
    }

    /**
     * Get active groups list
     */
    Set<String> getActiveGroups() {
        if (hubManager != null) {
            return hubManager.getGroupNames();
        }
        return Collections.emptySet();
    }

    // ================ Resource Cleanup ================

    public void destroy() {
        log.debug("Shutting down hub: {}", hubName);

        try {
            // 1. Stop heartbeat scheduler
            heartbeatScheduler.shutdownNow();
            // 2. Stop async executor
            asyncExecutor.shutdown();
            if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
            // 3. Clean up all connections and method cache
            connections.clear();
            userConnections.clear();
            methodHandlers.clear();
            log.debug("Hub {} shutdown completed", hubName);
        } catch (Exception e) {
            log.error("Error during hub {} shutdown", hubName, e);
        }
    }
}
