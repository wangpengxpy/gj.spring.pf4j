/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;

import gj.pf4j.socketio.cluster.IConnectionEventHandler;
import gj.pf4j.socketio.cluster.IMessageRouter;
import gj.pf4j.socketio.cluster.ITargetResolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
@ConditionalOnBean(SocketIOServer.class)
public class GJHubManager {

    private final Environment env;

    private static final Logger log = LoggerFactory.getLogger(GJHubManager.class);

    // ================ Core Data Structures ================

    // Hub registry: Hub name -> Hub instance
    private final Map<String, GJHub> hubRegistry = new ConcurrentHashMap<>();

    // Client registry: Hub name -> {connection ID -> client}
    private final Map<String, Map<String, SocketIOClient>> clientRegistry = new ConcurrentHashMap<>();

    // Hub context cache: Hub name -> Hub context
    private final Map<String, GJHubContext<? extends GJHub>> hubContexts = new ConcurrentHashMap<>();

    // User connection mapping: user ID -> [set of connection IDs]
    private final Map<String, Set<String>> userConnections = new ConcurrentHashMap<>();

    // Group connection mapping: group name -> [set of connection IDs]
    private final Map<String, Set<String>> groupConnections = new ConcurrentHashMap<>();

    // Connection info cache: connection ID -> connection info
    private final Map<String, GJSocketConnectionInfo> connectionInfoCache = new ConcurrentHashMap<>();

    // ================ Cluster Strategy Injection ================

    private final IMessageRouter messageRouter;
    private final ITargetResolver targetResolver;
    private final IConnectionEventHandler eventHandler;

    // ================ Performance Statistics ================

    private final AtomicLong totalMessagesSent = new AtomicLong(0);
    private final AtomicLong totalMessagesReceived = new AtomicLong(0);
    private final AtomicInteger peakConnections = new AtomicInteger(0);

    // ================ Dependency Injection ================

    private final SocketIOServer server;
    private final ExecutorService asyncExecutor;
    private final ScheduledExecutorService statsScheduler;

    private final GJSocketIOProperties socketIOProperties;
    private final GJHubSocketConnectionRateLimiter connectionRateLimiter;

    // ================ Shutdown Flag ================

    private volatile boolean shuttingDown = false;

    // ================ Configuration Constants ================

    private static final int CONNECTION_OR_DISCONNECTION_TIMEOUT = 30;
    private static final int STATS_INTERVAL_MINUTES = 5;
    private static final int DEFAULT_THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 4;

    @Autowired
    public GJHubManager(SocketIOServer server, Environment env,
                        GJSocketIOProperties socketIOProperties,
                        GJHubSocketConnectionRateLimiter connectionRateLimiter,
                        @Lazy IMessageRouter messageRouter,
                        @Lazy ITargetResolver targetResolver,
                        @Lazy IConnectionEventHandler eventHandler) {
        this.server = server;
        this.env = env;
        this.socketIOProperties = socketIOProperties;
        this.connectionRateLimiter = connectionRateLimiter;
        this.messageRouter = messageRouter;
        this.targetResolver = targetResolver;
        this.eventHandler = eventHandler;

        // Initialize thread pool
        this.asyncExecutor = createThreadPool();

        this.statsScheduler = Executors.newScheduledThreadPool(1,
                new GJSocketIOThreadFactory.Builder()
                        .setNameFormat("hub-manager-stats-%d")
                        .setDaemon(true)
                        .build());
    }

    private void updatePeakConnections() {
        int current = connectionInfoCache.size();
        peakConnections.updateAndGet(peak -> Math.max(peak, current));
    }

    private ExecutorService createThreadPool() {
        ThreadFactory threadFactory = new GJSocketIOThreadFactory.Builder()
                .setNameFormat("hub-manager-worker" + "-%d")
                .setDaemon(true)
                .setUncaughtExceptionHandler((thread, throwable) -> {
                    log.error("Uncaught exception in thread {}: {}", thread.getName(), throwable.getMessage(), throwable);
                })
                .build();

        return new ThreadPoolExecutor(
                DEFAULT_THREAD_POOL_SIZE / 2,
                DEFAULT_THREAD_POOL_SIZE,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @PostConstruct
    public void initialize() {
        log.info("HubManager initializing...");

        try {
            // 1. Set up event listeners
            setupEventListeners();
            // 2. Start stats scheduler
            startStatsScheduler();
            // 3. Start server
            server.start();
            log.info("HubManager initialized successfully");
            log.info("   SocketIO Server: {}:{}",
                    server.getConfiguration().getHostname(),
                    server.getConfiguration().getPort());
            log.info("   Thread Pool Size: {}", DEFAULT_THREAD_POOL_SIZE);

        } catch (Exception e) {
            log.error("HubManager initialization failed", e);
            throw new RuntimeException("HubManager initialization failed", e);
        }
    }

    private void startStatsScheduler() {
        statsScheduler.scheduleAtFixedRate(() -> {
            try {
                logStats();
            } catch (Exception e) {
                log.warn("Error logging stats", e);
            }
        }, STATS_INTERVAL_MINUTES, STATS_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    private void logStats() {
        int currentConnections = connectionInfoCache.size();
        long totalClients = server.getAllClients().size();
        long sentTotalMessages = totalMessagesSent.get();
        long receivedMessages = totalMessagesReceived.get();
        int peak = peakConnections.get();
        int hubCount = hubRegistry.size();

        log.info("HubManager Statistics ===================================");
        log.info("  Active Hubs: {}", hubCount);
        log.info("  Active Connections: {} (Peak: {})", currentConnections, peak);
        log.info("  Total Connections: {}", totalClients);
        log.info("  Messages Sent: {}", sentTotalMessages);
        log.info("  Messages Received: {}", receivedMessages);
        log.info("  Memory Usage: {}/{} MB",
                (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024,
                Runtime.getRuntime().totalMemory() / 1024 / 1024);
        log.info("==========================================================");

        // Update peak value
        if (currentConnections > peak) {
            peakConnections.set(currentConnections);
        }
    }

    // ================ Hub Registration Management ================

    synchronized boolean registerHub(GJHub hub) {
        if (hub == null) {
            log.warn("Invalid hub registration attempt - hub or hub name is null");
            return false;
        }
        String hubName = hub.getHubName();
        if (hubName == null || hubName.isEmpty()) {
            log.warn("Invalid hub registration attempt - hub name is null or empty");
            return false;
        }
        // Check if already registered
        if (hubRegistry.containsKey(hubName)) {
            log.warn("Hub '{}' is already registered", hubName);
            return false;
        }
        try {
            // 1. Write to Hub registry
            hubRegistry.put(hubName, hub);
            // 2. Inject hubManager reference so Hub inner classes can delegate group operations to the global single source of truth
            hub.setHubManager(this);
            // 3. Write to Hub client registry
            clientRegistry.put(hubName, new ConcurrentHashMap<>());
            // 4. Create Hub context
            createHubContext(hub);
            log.info("Hub registered: {} (type: {})",
                    hubName, hub.getClass().getSimpleName());
            return true;
        } catch (Exception e) {
            log.error("Failed to register hub: {}", hubName, e);
            hubRegistry.remove(hubName);
            clientRegistry.remove(hubName);
            return false;
        }
    }

    /**
     * Internal use only. Package-private.
     */
    public synchronized void unregisterHub(String hubName) {
        GJHub hub = hubRegistry.get(hubName);
        if (hub == null) {
            log.warn("Hub '{}' not found for unregister", hubName);
            return;
        }
        try {
            // 1. Clean up all Hub connection info and cache
            hub.destroy();
            // 2. Notify all clients that Hub is going offline
            notifyHubUnavailable(hubName);
            // 3. Remove Hub registration
            hubRegistry.remove(hubName);
            // 4. Clean up Hub client registry
            Map<String, SocketIOClient> clients = clientRegistry.remove(hubName);
            if (clients != null) {
                cleanupHubClients(clients.values());
            }
            // 5. Clean up Hub context
            hubContexts.remove(hubName);
            // 6. Clean up Hub connection info cache
            cleanupConnectionInfoForHub(hubName);
            log.debug("Hub unregistered: {}", hubName);

        } catch (Exception e) {
            log.error("Failed to unregister hub: {}", hubName, e);
        }
    }

    /**
     * Internal use only. Package-private.
     */
    public void registerHubs(Collection<GJHub> hubs) {
        int successCount = 0;
        for (GJHub hub : hubs) {
            if (registerHub(hub)) {
                successCount++;
            }
        }
        log.info("Batch registration completed: {}/{} hubs registered", successCount, hubs.size());
    }

    // ================ Client Connection Management ================

    private void handleClientConnected(SocketIOClient client) {
        // 1. Max connection flow control
        int maxConnections = socketIOProperties.getMaxConnections();
        int current = server.getAllClients().size();
        if (current >= maxConnections) {
            log.warn("Max connections ({}) exceeded. Current: {}. Rejecting.", maxConnections, current);
            client.disconnect();
            return;
        }
        log.debug("Accepted connection. Total: {}", current);
        // 2. Connection rate limiting per second (default: 100 TPS)
        int maxConnectionsPerSecond = socketIOProperties.getMaxConnectionsPerSecond();
        if (!connectionRateLimiter.tryAcquire()) {
            log.warn("New connection rejected: connection rate limit reached ({} / sec). Please check client behavior or adjust maxConnectionsPerSecond configuration.", maxConnectionsPerSecond);
            client.disconnect();
            return;
        }
        String connectionId = client.getSessionId().toString();
        long startTime = System.currentTimeMillis();
        try {
            // 1. Get connection parameters
            String hubName = getHubNameFromHandshake(client);
            if (hubName == null || hubName.isEmpty()) {
                log.error("Client {} missing hub parameter, disconnecting", connectionId);
                client.disconnect();
                return;
            }
            hubName = hubName.toLowerCase();
            String userId = client.getHandshakeData().getSingleUrlParam("userName");
            if (env.acceptsProfiles(Profiles.of("dev | debug"))) {
                userId = "test";
            } else if (userId == null || userId.isEmpty()) {
                log.warn("Client {} missing userId parameter, closing connection", connectionId);
                client.disconnect();
                return;
            }
            log.debug("Client connecting - ID: {}, Hub: {}, User: {}",
                    connectionId, hubName, userId);
            // 2. Check if Hub exists
            GJHub hub = hubRegistry.get(hubName);
            if (hub == null) {
                log.warn("Client {} tried to connect to unknown hub: {}, disconnecting",
                        connectionId, hubName);
                client.disconnect();
                return;
            }
            // 3. Store connection info
            GJSocketConnectionInfo connectionInfo = new GJSocketConnectionInfo(
                    connectionId, hubName, userId, client
            );
            connectionInfoCache.put(connectionId, connectionInfo);
            // 4. Register to client registry
            clientRegistry.computeIfAbsent(hubName, k -> new ConcurrentHashMap<>())
                    .put(connectionId, client);
            // 5. Register user connection
            userConnections.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                    .add(connectionId);
            // 6. Async call Hub connection handler
            CompletableFuture<Void> connectionFuture = hub.onClientConnectedAsync(client, userId);
            // 7. Set timeout and completion callback
            String finalUserId = userId;
            String finalHubName = hubName;
            connectionFuture.orTimeout(CONNECTION_OR_DISCONNECTION_TIMEOUT, TimeUnit.SECONDS)
                    .whenComplete((result, throwable) -> {
                        long cost = System.currentTimeMillis() - startTime;
                        if (throwable != null) {
                            log.error("Client {} connection handling failed in {}ms: {}",
                                    connectionId, cost, throwable.getMessage());
                            client.disconnect();
                        } else {
                            updatePeakConnections();
                            log.info("Client {} connected to hub {} in {}ms (User: {})",
                                    connectionId, finalHubName, cost, finalUserId);
                        }
                    });

            // 8. Cluster: notify connection established
            eventHandler.onConnected(connectionId, hubName, userId);

        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            log.error("Client {} connection failed in {}ms", connectionId, cost, e);
            client.disconnect();
        }
    }

    private void handleClientDisconnected(SocketIOClient client) {
        String connectionId = client.getSessionId().toString();

        try {
            GJSocketConnectionInfo connectionInfo = connectionInfoCache.get(connectionId);
            if (connectionInfo == null) {
                log.warn("No connection info found for disconnected client: {}", connectionId);
                return;
            }
            String hubName = connectionInfo.getHubName();
            String userId = connectionInfo.getUserId();
            log.debug("Client disconnecting - ID: {}, Hub: {}, User: {}",
                    connectionId, hubName, userId);
            // 1. Get the corresponding Hub
            GJHub hub = hubRegistry.get(hubName);
            if (hub != null) {
                // 2. Async call Hub disconnection handler
                CompletableFuture<Void> disconnectionFuture = hub.onClientDisconnectedAsync(client);
                disconnectionFuture.orTimeout(CONNECTION_OR_DISCONNECTION_TIMEOUT, TimeUnit.SECONDS)
                        .whenComplete((result, throwable) -> {
                            if (throwable != null) {
                                log.error("Client {} disconnection handling failed: {}",
                                        connectionId, throwable.getMessage());
                            } else {
                                log.info("Client {} disconnected from hub {} (User: {})",
                                        connectionId, hubName, userId);
                            }
                        });
            }
            // 3. Clean up registration info
            cleanupClientConnection(connectionId, hubName, userId);
        } catch (Exception e) {
            log.error("Client {} disconnection handling failed", connectionId, e);
        }
    }

    private void handleClientMessage(SocketIOClient client, JsonNode payload, AckRequest ackRequest) {
        totalMessagesReceived.incrementAndGet();

        String connectionId = client.getSessionId().toString();
        GJSocketConnectionInfo connectionInfo = connectionInfoCache.get(connectionId);
        if (connectionInfo == null) {
            log.error("No connection info for message from client: {}", connectionId);
            return;
        }
        String hubName = connectionInfo.getHubName();
        if (hubName == null || hubName.isEmpty()) {
            log.error("Client {} sent message without valid 'hub'", connectionId);
            return;
        }
        GJHub hub = hubRegistry.get(hubName);
        if (hub == null) {
            log.warn("Hub not found for client {}: {}", connectionId, hubName);
            return;
        }
        JsonNode methodNode = payload.get("method");
        if (methodNode == null || methodNode.isNull() || methodNode.asText().isEmpty()) {
            log.error("Client {} sent message without valid 'method' field (hub='{}')", connectionId, hubName);
            return;
        }
        String methodName = methodNode.asText().toLowerCase();
        JsonNode dataNode = payload.get("data");
        if (dataNode == null || dataNode.isNull()) {
            log.error("Client {} sent message with missing or null 'data' (hub='{}', method='{}')",
                    connectionId, hubName, methodName);
            return;
        }
        try {
            log.debug("Processing message from client {} to hub {}: {}",
                    connectionId, hubName, methodName);
            hub.onClientMessage(client, methodName, dataNode, ackRequest);
        } catch (Exception e) {
            log.error("Error processing message from client: {} to hub {}: {}", connectionId, hubName, methodName, e);
            if (client.isChannelOpen()) {
                try {
                    client.sendEvent(methodName, Map.of(
                            "error", "Message processing failed",
                            "success", false,
                            "type", 9
                    ));
                } catch (Exception sendError) {
                    log.warn("Failed to send error to client: {} to hub {}: {}", connectionId, hubName, methodName, sendError);
                }
            }
        }
    }

    // ================ Message Sending API ================

    /**
     * Core method for sending messages
     */
    void sendMessage(String hubName,
                     String method,
                     Object data,
                     Collection<String> targetConnectionIds,
                     Collection<String> targetGroups,
                     Collection<String> excludedConnectionIds,
                     Collection<String> targetUserIds,
                     Collection<String> excludedUserIds) {

        long startTime = System.currentTimeMillis();

        try {
            // 1. Verify Hub exists
            if (!hubRegistry.containsKey(hubName)) {
                log.warn("Cannot send message to unregistered hub: {}", hubName);
                return;
            }
            // 2. Get target connection set
            Set<String> targetConnections = calculateTargetConnections(
                    hubName, targetConnectionIds, targetGroups, excludedConnectionIds,
                    targetUserIds, excludedUserIds
            );

            if (targetConnections.isEmpty()) {
                log.debug("No target connections found for hub: {}, method: {}", hubName, method);
                return;
            }
            // 3. Build message
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("data", data);
            messageData.put("success", true);
            messageData.put("type", 3);
            // 4. Send message
            int successCount = sendToConnections(method, hubName, targetConnections, messageData);
            // 5. Update statistics
            totalMessagesSent.addAndGet(successCount);
            // 6. Calculate elapsed time
            long cost = System.currentTimeMillis() - startTime;
            log.debug("Message sent - Hub: {}, Method: {}, Targets: {}/{}, Cost: {}ms",
                    hubName, method, successCount, targetConnections.size(), cost);

        } catch (Exception e) {
            log.error("Failed to send message to hub: {}, method: {}", hubName, method, e);
        }
    }

    /**
     * Async send message
     */
    CompletableFuture<Integer> sendMessageAsync(String hubName,
                                                String method,
                                                Object data,
                                                Collection<String> targetConnectionIds,
                                                Collection<String> targetGroups,
                                                Collection<String> excludedConnectionIds,
                                                Collection<String> targetUserIds,
                                                Collection<String> excludedUserIds) {
        // 1. Verify Hub exists
        if (!hubRegistry.containsKey(hubName)) {
            log.warn("Cannot send message to unregistered hub={} method={}", hubName, method);
            return CompletableFuture.completedFuture(0);
        }
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("data", data);
            messageData.put("success", true);
            messageData.put("type", 3);
            // 2. Calculate target connections
            Set<String> targetConnections = calculateTargetConnections(
                    hubName, targetConnectionIds, targetGroups, excludedConnectionIds,
                    targetUserIds, excludedUserIds
            );
            if (targetConnections.isEmpty()) {
                return 0;
            }
            // Send message
            return sendToConnections(method, hubName, targetConnections, messageData);
        }, asyncExecutor);
    }

    // ================ Group Management (global single source of truth) ================

    /**
     * Record connection joining a group (only operates on the global groupConnections map).
     * joinRoom is handled by the caller (the caller holds the SocketIOClient reference).
     */
    void addToGroup(String connectionId, String groupName) {
        groupConnections.computeIfAbsent(groupName, k -> ConcurrentHashMap.newKeySet())
                .add(connectionId);
    }

    /**
     * Record connection leaving a group (only operates on the global groupConnections map).
     * leaveRoom is handled by the caller.
     */
    void removeFromGroup(String connectionId, String groupName) {
        Set<String> members = groupConnections.get(groupName);
        if (members != null) {
            members.remove(connectionId);
            if (members.isEmpty()) {
                groupConnections.remove(groupName);
            }
        }
    }

    /**
     * Remove a connection from all groups (used for disconnection cleanup).
     */
    void removeFromAllGroups(String connectionId) {
        for (Set<String> members : groupConnections.values()) {
            members.remove(connectionId);
        }
        groupConnections.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /**
     * Get all client mappings for the specified hub (for internal classes like InternalClientProxy).
     */
    Map<String, SocketIOClient> getHubClients(String hubName) {
        return clientRegistry.get(hubName);
    }

    Map<String, Map<String, SocketIOClient>> getClientRegistry() {
        return clientRegistry;
    }

    Map<String, Set<String>> getGroupConnections() {
        return groupConnections;
    }

    Map<String, Set<String>> getUserConnections() {
        return userConnections;
    }

    Set<String> getGroupNames() {
        return Collections.unmodifiableSet(groupConnections.keySet());
    }

    int getGroupConnectionsCount() {
        return groupConnections.size();
    }

    // ================ Group Management API (Async) ================

    CompletableFuture<Boolean> addConnectionToGroupAsync(String hubName,
                                                         String connectionId,
                                                         String groupName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Verify connection belongs to this Hub
                Map<String, SocketIOClient> hubClients = clientRegistry.get(hubName);
                if (hubClients == null || !hubClients.containsKey(connectionId)) {
                    log.warn("Connection {} not found in hub {}", connectionId, hubName);
                    return false;
                }
                // 2. Get client
                SocketIOClient client = hubClients.get(connectionId);
                if (client == null || !client.isChannelOpen()) {
                    log.warn("Connection {} is not active", connectionId);
                    return false;
                }
                // 3. Join room
                client.joinRoom(groupName);
                // 4. Update group connection mapping
                groupConnections.computeIfAbsent(groupName, k -> ConcurrentHashMap.newKeySet())
                        .add(connectionId);
                // 5. Cluster: notify group join
                eventHandler.onGroupChanged(connectionId, groupName, true);
                log.debug("Connection {} added to group {} in hub {}",
                        connectionId, groupName, hubName);
                return true;
            } catch (Exception e) {
                log.error("Failed to add connection {} to group {}: {}",
                        connectionId, groupName, e.getMessage());
                return false;
            }
        }, asyncExecutor);
    }

    CompletableFuture<Boolean> isInGroupAsync(String connectionId, String groupName) {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> members = groupConnections.get(groupName);
            return members != null && members.contains(connectionId);
        }, asyncExecutor);
    }

    public CompletableFuture<Set<String>> getGroupsForConnectionAsync(String connectionId) {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> groups = new HashSet<>();
            for (Map.Entry<String, Set<String>> entry : groupConnections.entrySet()) {
                if (entry.getValue().contains(connectionId)) {
                    groups.add(entry.getKey());
                }
            }
            return Collections.unmodifiableSet(groups);
        }, asyncExecutor);
    }

    CompletableFuture<Boolean> removeConnectionFromGroupAsync(String hubName,
                                                              String connectionId,
                                                              String groupName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Get client
                Map<String, SocketIOClient> hubClients = clientRegistry.get(hubName);
                if (hubClients == null) {
                    return false;
                }
                SocketIOClient client = hubClients.get(connectionId);
                if (client != null && client.isChannelOpen()) {
                    // 2. Leave room
                    client.leaveRoom(groupName);
                }
                // 3. Update group connection mapping
                Set<String> groupMembers = groupConnections.get(groupName);
                if (groupMembers != null) {
                    groupMembers.remove(connectionId);
                }
                // 4. Cluster: notify group leave
                eventHandler.onGroupChanged(connectionId, groupName, false);
                log.debug("Connection {} removed from group {} in hub {}",
                        connectionId, groupName, hubName);
                return true;
            } catch (Exception e) {
                log.error("Failed to remove connection {} from group {}: {}",
                        connectionId, groupName, e.getMessage());
                return false;
            }
        }, asyncExecutor);
    }

    // ================ Query API ================

    private boolean isHubRegistered(String hubName) {
        return hubRegistry.containsKey(hubName);
    }

    private int getHubClientsCount(String hubName) {
        Map<String, SocketIOClient> clients = clientRegistry.get(hubName);
        return clients != null ? clients.size() : 0;
    }

    private int getTotalConnectionsCount() {
        return connectionInfoCache.size();
    }

    Set<String> getConnectionIdsInGroup(String groupName) {
        Set<String> members = groupConnections.get(groupName);
        return members != null ? Collections.unmodifiableSet(members) : Collections.emptySet();
    }

    Set<String> getConnectionIdsForUser(String userId) {
        Set<String> connections = userConnections.get(userId);
        return connections != null ? Collections.unmodifiableSet(connections) : Collections.emptySet();
    }

    Optional<GJSocketConnectionInfo> getConnectionInfo(String connectionId) {
        return Optional.ofNullable(connectionInfoCache.get(connectionId));
    }

    // ================ Context Retrieval ================

    @SuppressWarnings("unchecked")
    public <T extends GJHub> GJHubContext<T> getHubContext(Class<T> hubClass) {
        if (hubClass == null) {
            throw new IllegalArgumentException("Hub class must not be null");
        }
        GJHub iotHub;
        try {
            iotHub = getHubInstance(hubClass);
        } catch (IllegalStateException e) {
            if (shuttingDown) {
                return new NoopGJHubContext<>();
            }
            log.error("{} is not registered or does not implement GJHub", hubClass.getName(), e);
            throw new IllegalStateException(
                    hubClass.getName() + " is not registered or does not implement GJHub. Check if the plugin is correctly registered.", e);
        }
        String hubName = iotHub.getHubName();
        if (hubName == null || hubName.isEmpty()) {
            throw new IllegalStateException("No GJHub registered for class: " + hubClass.getName() +
                    ". Did you forget to register it as a @Component?");
        }
        return (GJHubContext<T>) hubContexts.get(hubName);
    }

    <T extends GJHub> T getHubInstance(Class<T> hubClass) {
        if (hubClass == null) {
            throw new IllegalArgumentException("Hub class must not be null");
        }
        long start = System.currentTimeMillis();
        try {
            T result = hubRegistry.values().stream()
                    .filter(hubClass::isInstance)
                    .map(hubClass::cast)
                    .findFirst()
                    .orElse(null);

            long elapsed = System.currentTimeMillis() - start;

            if (result != null) {
                if (log.isDebugEnabled()) {
                    log.debug("Found GJHub instance of type {} in {} ms",
                            hubClass.getSimpleName(), elapsed);
                }
                return result;
            } else {
                // Record all currently registered Hub types for troubleshooting
                Collection<String> registeredTypes = hubRegistry.values().stream()
                        .map(hub -> hub.getClass().getName())
                        .collect(Collectors.toList());

                log.debug("No GJHub instance found for type {} after {} ms. " +
                                "Currently registered hub types: {}",
                        hubClass.getName(), elapsed, registeredTypes);

                throw new IllegalStateException(
                        "No registered GJHub implements " + hubClass.getName());
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Error while looking up GJHub instance for type {} (took {} ms)",
                    hubClass.getName(), elapsed, e);
            throw e;
        }
    }

// ================ Private Helper Methods ================

    private void setupEventListeners() {
        server.addConnectListener(this::handleClientConnected);
        server.addDisconnectListener(this::handleClientDisconnected);
        server.addEventListener("invoke", JsonNode.class, this::handleClientMessage);
        log.debug("SocketIO event listeners setup completed");
    }

    private void createHubContext(GJHub hub) {
        String hubName = hub.getHubName();
        try {
            GJGenericHubContext<? extends GJHub> hubContext = new GJGenericHubContext<>(hubName, this);
            hubContexts.put(hubName, hubContext);
            log.debug("Created HubContext for: {}", hubName);
        } catch (Exception e) {
            log.error("Failed to create HubContext for {}: {}", hubName, e.getMessage());
        }
    }

    private Set<String> calculateTargetConnections(String hubName,
                                                   Collection<String> targetConnectionIds,
                                                   Collection<String> targetGroups,
                                                   Collection<String> excludedConnectionIds,
                                                   Collection<String> targetUserIds,
                                                   Collection<String> excludedUserIds) {

        Set<String> result = new HashSet<>();
        // 1. If connection IDs are specified, use them directly
        if (targetConnectionIds != null && !targetConnectionIds.isEmpty()) {
            result.addAll(targetConnectionIds);
        }
        // 2. If groups are specified, add connections from those groups
        else if (targetGroups != null && !targetGroups.isEmpty()) {
            for (String groupName : targetGroups) {
                Set<String> groupMembers = groupConnections.get(groupName);
                if (groupMembers != null) {
                    result.addAll(groupMembers);
                }
            }
        }
        // 3. If users are specified, add user connections
        else if (targetUserIds != null && !targetUserIds.isEmpty()) {
            for (String userId : targetUserIds) {
                Set<String> userConnIds = userConnections.get(userId);
                if (userConnIds != null) {
                    result.addAll(userConnIds);
                }
            }
        }
        // 4. Default: all connections for this Hub
        else {
            Map<String, SocketIOClient> hubClients = clientRegistry.get(hubName);
            if (hubClients != null) {
                result.addAll(hubClients.keySet());
            }
        }
        // 5. Exclude connections
        if (excludedConnectionIds != null && !excludedConnectionIds.isEmpty()) {
            result.removeAll(excludedConnectionIds);
        }
        // 6. Exclude users
        if (excludedUserIds != null && !excludedUserIds.isEmpty()) {
            Set<String> connectionsToRemove = new HashSet<>();
            for (String userId : excludedUserIds) {
                Set<String> userConnIds = userConnections.get(userId);
                if (userConnIds != null) {
                    connectionsToRemove.addAll(userConnIds);
                }
            }
            result.removeAll(connectionsToRemove);
        }
        // 7. Filter out connections that don't belong to this Hub
        Map<String, SocketIOClient> hubClients = clientRegistry.get(hubName);
        if (hubClients != null) {
            result.retainAll(hubClients.keySet());
        }
        // 8. Cluster mode: append remote targets
        result.addAll(targetResolver.resolveTargets(hubName, targetGroups, targetUserIds));
        return result;
    }

    private int sendToConnections(String method, String hubName, Set<String> connectionIds, Map<String, Object> message) {
        Map<String, SocketIOClient> hubClients = clientRegistry.get(hubName);
        if (hubClients == null) {
            return 0;
        }
        int successCount = 0;
        List<String> failedConnections = new ArrayList<>();

        for (String connectionId : connectionIds) {
            long start = System.nanoTime();
            if (messageRouter.sendToConnection(hubName, connectionId, method, message)) {
                long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
                successCount++;
                log.info("Sent to connection {} in hub={} method={}, took {} ms",
                        connectionId, hubName, method, elapsedMillis);
            } else {
                failedConnections.add(connectionId);
            }
        }
        // Clean up failed connections
        if (!failedConnections.isEmpty()) {
            asyncExecutor.submit(() -> {
                for (String failedConn : failedConnections) {
                    cleanupStaleConnection(failedConn);
                }
            });
        }
        return successCount;
    }

    private void cleanupClientConnection(String connectionId, String hubName, String userId) {
        // 0. Snapshot groups before cleanup (for cluster disconnect hook)
        Set<String> groupsSnapshot = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : groupConnections.entrySet()) {
            if (entry.getValue().contains(connectionId)) {
                groupsSnapshot.add(entry.getKey());
            }
        }
        // 1. Remove from client registry
        Map<String, SocketIOClient> hubClients = clientRegistry.get(hubName);
        if (hubClients != null) {
            hubClients.remove(connectionId);
        }
        // 2. Remove from user connections
        if (userId != null && !userId.isEmpty()) {
            Set<String> users = userConnections.get(userId);
            if (users != null) {
                users.remove(connectionId);
                if (users.isEmpty()) {
                    userConnections.remove(userId);
                }
            }
        }
        // 3. Remove from all groups
        for (Set<String> groupMembers : groupConnections.values()) {
            groupMembers.remove(connectionId);
        }
        // 4. Clean up empty groups
        groupConnections.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        // 5. Remove from connection info cache
        connectionInfoCache.remove(connectionId);
        log.debug("Cleaned up connection: {}", connectionId);
        // 6. Cluster: notify disconnection
        eventHandler.onDisconnected(connectionId, hubName, userId, groupsSnapshot);
    }

    private void cleanupStaleConnection(String connectionId) {
        GJSocketConnectionInfo info = connectionInfoCache.get(connectionId);
        if (info != null) {
            cleanupClientConnection(connectionId, info.getHubName(), info.getUserId());
            log.debug("Cleaned up stale connection: {}", connectionId);
        }
    }

    private void cleanupHubClients(Collection<SocketIOClient> clients) {
        for (SocketIOClient client : clients) {
            if (client.isChannelOpen()) {
                try {
                    client.disconnect();
                } catch (Exception e) {
                    // Ignore disconnection error
                }
            }
        }
    }

    private void cleanupConnectionInfoForHub(String hubName) {
        List<String> connectionsToRemove = new ArrayList<>();
        for (Map.Entry<String, GJSocketConnectionInfo> entry : connectionInfoCache.entrySet()) {
            if (hubName.equals(entry.getValue().getHubName())) {
                connectionsToRemove.add(entry.getKey());
            }
        }
        for (String connectionId : connectionsToRemove) {
            connectionInfoCache.remove(connectionId);
        }
    }

    private void notifyHubUnavailable(String hubName) {
        Map<String, SocketIOClient> clients = clientRegistry.get(hubName);
        if (clients == null) {
            return;
        }
        Map<String, Object> notification = Map.of(
                "success", false,
                "error", "HUB_UNAVAILABLE(Hub is being unloaded)",
                "type", 9
        );

        for (SocketIOClient client : clients.values()) {
            if (client.isChannelOpen()) {
                try {
                    client.sendEvent("error", notification);
                } catch (Exception e) {
                    // Ignore send error
                }
            }
        }
    }


    private String getHubNameFromHandshake(SocketIOClient client) {
        return client.getHandshakeData().getSingleUrlParam("hub");
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        log.info("HubManager shutting down...");

        // 0. Cluster: stop heartbeat, unsubscribe, clean Redis node state
        eventHandler.shutdown();

        // 1. Stop stats scheduler
        statsScheduler.shutdownNow();

        // 2. Shutdown async thread pool
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // 3. Destroy all registered hubs
        for (Map.Entry<String, GJHub> entry : hubRegistry.entrySet()) {
            try {
                entry.getValue().destroy();
            } catch (Exception e) {
                log.error("Error destroying hub: {}", entry.getKey(), e);
            }
        }
        hubRegistry.clear();
        clientRegistry.clear();
        hubContexts.clear();
        userConnections.clear();
        groupConnections.clear();
        connectionInfoCache.clear();

        // 4. Stop Socket.IO server
        if (server != null) {
            server.stop();
            log.info("Socket.IO server stopped");
        }

        log.info("HubManager shutdown completed");
    }
}