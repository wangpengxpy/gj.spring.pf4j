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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
    protected final Map<String, ConnectionContext> connections = new ConcurrentHashMap<>();
    protected final Map<String, Set<String>> userConnections = new ConcurrentHashMap<>();

    // Thread management
    protected ThreadFactory threadFactory;
    protected ExecutorService asyncExecutor;
    protected ScheduledExecutorService heartbeatScheduler;

    // Thread-local variables for storing current request context
    private final ThreadLocal<ConnectionContext> currentContext = new ThreadLocal<>();
    private final ThreadLocal<GJHubCallerClients> currentClients = new ThreadLocal<>();
    private final ThreadLocal<GJGroupManager> currentGroups = new ThreadLocal<>();

    // Monitoring statistics
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong totalMessages = new AtomicLong(0);

    // Heartbeat interval timeout (in minutes)
    private static final int HEARTBEAT_INTERVAL_TIMEOUT = 5;

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
        log.info("Hub '{}' registered {} @IoTHubMethod(s) in {} ms", hubName, methodCount, durationMillis);
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
                ConnectionContext context = new ConnectionContext(
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
            try {
                long startTime = System.currentTimeMillis();
                // 1. Get connection context
                ConnectionContext context = connections.get(connectionId);
                if (context == null) {
                    log.warn("No context found for disconnected client: {}", connectionId);
                    return;
                }
                // 2. Set current thread context
                setCurrentContext(context);
                // 3. Call subclass disconnection handler
                CompletableFuture<Void> future = onDisconnectedAsync();
                CompletableFuture<Void> safeFuture = future != null ? future : CompletableFuture.completedFuture(null);
                safeFuture.get();
                // 4. Clean up connection
                cleanupConnection(connectionId, context.getUserId());
                // 5. Update statistics
                activeConnections.decrementAndGet();
                long cost = System.currentTimeMillis() - startTime;
                log.info("Client {} disconnected from {} in {}ms (User: {})",
                        connectionId, hubName, cost, context.getUserId());
            } catch (Exception e) {
                log.error("Failed to handle disconnection for client {}: {}", connectionId, e.getMessage(), e);
            } finally {
                clearCurrentContext();
            }
        }, asyncExecutor);
    }

    void onClientMessage(SocketIOClient client, String method, Object data, AckRequest ack) {
        String connectionId = client.getSessionId().toString();
        totalMessages.incrementAndGet();
        ConnectionContext context = connections.get(connectionId);
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
    private void setCurrentContext(ConnectionContext context) {
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
        ConnectionContext context = currentContext.get();
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
            ConnectionContext context = connections.get(connectionId);
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
        ConnectionContext context = connections.get(connectionId);
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
        int corePoolSize = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        int maxPoolSize = Runtime.getRuntime().availableProcessors() * 2;

        return new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
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

    // ================ Inner Classes ================

    /**
     * Connection context
     */
    public static class ConnectionContext {
        private final String connectionId;
        private final String userId;
        private final SocketIOClient client;
        private final GJHubCallerContext hubCallerContext;
        private volatile long lastActivityTime;

        public ConnectionContext(String connectionId, String userId,
                                 SocketIOClient client, Map<String, String> queryParams) {
            this.connectionId = connectionId;
            this.userId = userId;
            this.client = client;
            this.hubCallerContext = new GJHubCallerContext(connectionId, userId, queryParams);
            this.lastActivityTime = System.currentTimeMillis();
        }

        public String getConnectionId() {
            return connectionId;
        }

        public String getUserId() {
            return userId;
        }

        public SocketIOClient getClient() {
            return client;
        }

        public GJHubCallerContext getHubCallerContext() {
            return hubCallerContext;
        }

        public long getLastActivityTime() {
            return lastActivityTime;
        }

        public void updateLastActivity() {
            this.lastActivityTime = System.currentTimeMillis();
        }
    }
}

// ================ Internal Hub Client Proxy ================

class InternalHubClients implements GJHubCallerClients {
    private static final Logger log = LoggerFactory.getLogger(InternalHubClients.class);

    private final String currentConnectionId;
    private final String currentUserId;
    private final SocketIOClient currentClient;
    private final String hubName;
    private final GJHubManager hubManager;
    private final ExecutorService asyncExecutor;

    public InternalHubClients(String currentConnectionId, String currentUserId,
                              SocketIOClient currentClient, String hubName,
                              GJHubManager hubManager, ExecutorService asyncExecutor) {
        this.currentConnectionId = currentConnectionId;
        this.currentUserId = currentUserId;
        this.currentClient = currentClient;
        this.hubName = hubName;
        this.hubManager = hubManager;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    public GJClientProxy all() {
        return new InternalClientProxy(hubName, null, null, null, false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy caller() {
        return new InternalClientProxy(hubName,
                Collections.singleton(currentConnectionId), null, null, false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy others() {
        return new InternalClientProxy(hubName,
                null, null, Collections.singleton(currentConnectionId), false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy client(String connectionId) {
        return new InternalClientProxy(hubName,
                Collections.singleton(connectionId), null, null, false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy clients(Collection<String> connectionIds) {
        return new InternalClientProxy(hubName,
                connectionIds, null, null, false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy group(String groupName) {
        return new InternalClientProxy(hubName,
                null, Collections.singleton(groupName), null, false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy groups(Collection<String> groupNames) {
        return new InternalClientProxy(hubName,
                null, groupNames, null, false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy othersInGroup(String groupName) {
        return new InternalClientProxy(hubName,
                null, Collections.singleton(groupName),
                Collections.singleton(currentConnectionId), false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy othersInGroups(Collection<String> groupNames) {
        return new InternalClientProxy(hubName,
                null, groupNames, Collections.singleton(currentConnectionId), false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy user(String userId) {
        return new InternalClientProxy(hubName,
                null, null, null, false, Collections.singleton(userId), null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy users(Collection<String> userIds) {
        return new InternalClientProxy(hubName,
                null, null, null, false, userIds, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy groupExceptUser(String groupName, String userId) {
        return new InternalClientProxy(hubName,
                null, Collections.singleton(groupName), null, false, null,
                Collections.singleton(userId), hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy allExcept(Collection<String> excludedConnectionIds) {
        return new InternalClientProxy(hubName,
                null, null, excludedConnectionIds, false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy groupExceptUsers(String groupName, Collection<String> excludedUserIds) {
        return new InternalClientProxy(hubName,
                null, Collections.singleton(groupName), null, false, null, excludedUserIds, hubManager, asyncExecutor);
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public SocketIOClient getCurrentClient() {
        return currentClient;
    }
}

// ================ Internal Client Proxy ================

class InternalClientProxy implements GJClientProxy {
    private static final Logger log = LoggerFactory.getLogger(InternalClientProxy.class);

    private final String hubName;
    private final Collection<String> targetConnectionIds;
    private final Collection<String> targetGroups;
    private final Collection<String> excludedConnectionIds;
    private final boolean excludeCaller;
    private final Collection<String> targetUserIds;
    private final Collection<String> excludedUserIds;
    private final GJHubManager hubManager;
    private final ExecutorService asyncExecutor;

    public InternalClientProxy(String hubName,
                               Collection<String> targetConnectionIds,
                               Collection<String> targetGroups,
                               Collection<String> excludedConnectionIds,
                               boolean excludeCaller,
                               Collection<String> targetUserIds,
                               Collection<String> excludedUserIds,
                               GJHubManager hubManager,
                               ExecutorService asyncExecutor) {
        this.hubName = hubName;
        this.targetConnectionIds = targetConnectionIds;
        this.targetGroups = targetGroups;
        this.excludedConnectionIds = excludedConnectionIds;
        this.excludeCaller = excludeCaller;
        this.targetUserIds = targetUserIds;
        this.excludedUserIds = excludedUserIds;
        this.hubManager = hubManager;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    public CompletableFuture<Void> sendAsync(String method, Object data) {
        return CompletableFuture.runAsync(() -> {
            try {
                Map<String, Object> message = new HashMap<>();
                if (data != null) {
                    message.put("data", data);
                    message.put("success", true);
                    message.put("error", "");
                    message.put("type", 3);
                }
                List<SocketIOClient> targetClients = getTargetClients();
                for (SocketIOClient client : targetClients) {
                    if (client.isChannelOpen()) {
                        try {
                            client.sendEvent(method, message);
                        } catch (Exception e) {
                            log.error("Failed to send to client: {} in hub {}:{} ", client.getSessionId(), hubName, method, e);
                        }
                    }
                }
                log.debug("Sent message {} to {} clients in hub {}",
                        method, targetClients.size(), hubName);
            } catch (Exception e) {
                log.error("Failed to send message {} in hub {}", method, hubName, e);
                throw new RuntimeException("Failed to send message", e);
            }
        }, asyncExecutor);
    }

    /**
     * Calculate target client list, query through GJHubManager, and apply exclusion logic.
     */
    private List<SocketIOClient> getTargetClients() {
        Map<String, SocketIOClient> hubClients = hubManager.getHubClients(hubName);
        if (hubClients == null || hubClients.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> targetIds;

        if (targetConnectionIds != null && !targetConnectionIds.isEmpty()) {
            targetIds = new HashSet<>(targetConnectionIds);
        } else if (targetGroups != null && !targetGroups.isEmpty()) {
            targetIds = new HashSet<>();
            for (String group : targetGroups) {
                targetIds.addAll(hubManager.getConnectionIdsInGroup(group));
            }
        } else if (targetUserIds != null && !targetUserIds.isEmpty()) {
            targetIds = new HashSet<>();
            for (String userId : targetUserIds) {
                targetIds.addAll(hubManager.getConnectionIdsForUser(userId));
            }
        } else {
            targetIds = new HashSet<>(hubClients.keySet());
        }

        // Exclude filtering
        if (excludedConnectionIds != null && !excludedConnectionIds.isEmpty()) {
            targetIds.removeAll(excludedConnectionIds);
        }
        if (excludedUserIds != null && !excludedUserIds.isEmpty()) {
            for (String userId : excludedUserIds) {
                targetIds.removeAll(hubManager.getConnectionIdsForUser(userId));
            }
        }

        // Resolve to SocketIOClient list
        List<SocketIOClient> result = new ArrayList<>();
        for (String id : targetIds) {
            SocketIOClient client = hubClients.get(id);
            if (client != null && client.isChannelOpen()) {
                result.add(client);
            }
        }
        return result;
    }

    public Collection<String> getExcludedConnectionIds() {
        return excludedConnectionIds;
    }

    public boolean isExcludeCaller() {
        return excludeCaller;
    }

    public Collection<String> getExcludedUserIds() {
        return excludedUserIds;
    }
}

// ================ Internal Group Manager ================

class InternalGroupManager implements GJGroupManager {
    private static final Logger log = LoggerFactory.getLogger(InternalGroupManager.class);

    private final String currentConnectionId;
    private final SocketIOClient currentClient;
    private final String hubName;
    private final GJHubManager hubManager;
    private final ExecutorService asyncExecutor;

    public InternalGroupManager(String currentConnectionId, SocketIOClient currentClient,
                                String hubName, GJHubManager hubManager,
                                ExecutorService asyncExecutor) {
        this.currentConnectionId = currentConnectionId;
        this.currentClient = currentClient;
        this.hubName = hubName;
        this.hubManager = hubManager;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    public CompletableFuture<Void> addToGroupAsync(String groupName) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 1. Join SocketIO room
                currentClient.joinRoom(groupName);
                // 2. Record in global HubManager (single source of truth)
                hubManager.addToGroup(currentConnectionId, groupName);
                log.debug("Connection {} added to group {} in hub {}",
                        currentConnectionId, groupName, hubName);
            } catch (Exception e) {
                log.error("Failed to add connection {} to group {}: {}",
                        currentConnectionId, groupName, e.getMessage());
                throw new RuntimeException("Failed to add to group", e);
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Void> addToGroupAsync(String connectionId, String groupName) {
        if (!currentConnectionId.equals(connectionId)) {
            log.warn("Cannot add other connections to group from current context. " +
                            "Attempted: connectionId='{}', currentConnectionId='{}', groupName='{}'",
                    connectionId, currentConnectionId, groupName);
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Cannot add other connection to group from current context"));
        }
        return addToGroupAsync(groupName);
    }

    @Override
    public CompletableFuture<Void> removeFromGroupAsync(String groupName) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 1. Leave SocketIO room
                currentClient.leaveRoom(groupName);
                // 2. Remove from global HubManager
                hubManager.removeFromGroup(currentConnectionId, groupName);
                log.debug("Connection {} removed from group {} in hub {}",
                        currentConnectionId, groupName, hubName);
            } catch (Exception e) {
                log.error("Failed to remove connection {} from group {}: {}",
                        currentConnectionId, groupName, e.getMessage());
                throw new RuntimeException("Failed to remove from group", e);
            }
        }, asyncExecutor);
    }

    @Override
    public CompletableFuture<Void> removeFromGroupAsync(String connectionId, String groupName) {
        if (!currentConnectionId.equals(connectionId)) {
            log.warn("Cannot remove other connections from group from current context. " +
                            "Attempted: connectionId='{}', currentConnectionId='{}', groupName='{}'",
                    connectionId, currentConnectionId, groupName);
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Cannot remove other connection from group from current context"));
        }
        return removeFromGroupAsync(groupName);
    }

    @Override
    public CompletableFuture<Boolean> isInGroupAsync(String groupName) {
        return CompletableFuture.supplyAsync(() ->
                hubManager.getConnectionIdsInGroup(groupName).contains(currentConnectionId), asyncExecutor);
    }

    @Override
    public CompletableFuture<Boolean> isInGroupAsync(String connectionId, String groupName) {
        if (!currentConnectionId.equals(connectionId)) {
            log.warn("Cannot check other connections' group membership from current context. " +
                            "Attempted: connectionId='{}', currentConnectionId='{}', groupName='{}'",
                    connectionId, currentConnectionId, groupName);
            return CompletableFuture.completedFuture(false);
        }
        return isInGroupAsync(groupName);
    }

    @Override
    public CompletableFuture<Set<String>> getGroupsForConnectionAsync() {
        return hubManager.getGroupsForConnectionAsync(currentConnectionId);
    }

    @Override
    public CompletableFuture<Set<String>> getGroupsForConnectionAsync(String connectionId) {
        if (!currentConnectionId.equals(connectionId)) {
            log.warn("Cannot get groups for other connections from current context. " +
                            "Attempted: connectionId='{}', currentConnectionId='{}'",
                    connectionId, currentConnectionId);
            return CompletableFuture.completedFuture(Collections.emptySet());
        }
        return getGroupsForConnectionAsync();
    }

    @Override
    public CompletableFuture<Set<String>> getConnectionsInGroupAsync(String groupName) {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> connectionIds = hubManager.getConnectionIdsInGroup(groupName);
            return connectionIds;
        }, asyncExecutor);
    }
}
