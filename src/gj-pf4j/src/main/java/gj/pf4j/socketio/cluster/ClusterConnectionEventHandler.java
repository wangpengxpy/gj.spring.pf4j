package gj.pf4j.socketio.cluster;

import com.corundumstudio.socketio.SocketIOClient;
import gj.pf4j.redis.IGJRedisBusService;
import gj.pf4j.redis.IGJRedisService;
import gj.pf4j.socketio.GJSocketIOThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ClusterConnectionEventHandler implements IConnectionEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ClusterConnectionEventHandler.class);

    private static final String CONN_KEY_PREFIX = "socketio:conn:";
    private static final String CONN_GROUPS_SUFFIX = ":groups";
    private static final String NODE_CONNS_PREFIX = "socketio:node:";
    private static final String NODE_CONNS_SUFFIX = ":connections";
    private static final String HEARTBEAT_KEY_PREFIX = "socketio:node:";
    private static final String HEARTBEAT_SUFFIX = ":heartbeat";
    private static final String GROUP_KEY_PREFIX = "socketio:group:";
    private static final String USER_KEY_PREFIX = "socketio:user:";
    private static final String BROADCAST_CHANNEL = "socketio:broadcast";

    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final int HEARTBEAT_TTL_SECONDS = 45;

    private final Map<String, Map<String, SocketIOClient>> clientRegistry;
    private final IGJRedisService redisService;
    private final IGJRedisBusService busService;
    private final String selfNodeId;
    private final int connectionTtl;

    private final ScheduledExecutorService heartbeatExecutor;
    private final StringRedisSerializer stringSerializer = new StringRedisSerializer();
    private final GenericJackson2JsonRedisSerializer jsonSerializer =
            new GenericJackson2JsonRedisSerializer();

    public ClusterConnectionEventHandler(Map<String, Map<String, SocketIOClient>> clientRegistry,
                                  IGJRedisService redisService,
                                  IGJRedisBusService busService,
                                  String selfNodeId,
                                  int connectionTtl) {
        this.clientRegistry = clientRegistry;
        this.redisService = redisService;
        this.busService = busService;
        this.selfNodeId = selfNodeId;
        this.connectionTtl = connectionTtl;
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(
                new GJSocketIOThreadFactory.Builder()
                        .setNameFormat("cluster-heartbeat-%d")
                        .setDaemon(true)
                        .build());
        startHeartbeat();
        subscribeBroadcast();
    }

    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                redisService.setWithTtl(
                        HEARTBEAT_KEY_PREFIX + selfNodeId + HEARTBEAT_SUFFIX,
                        String.valueOf(System.currentTimeMillis()),
                        Duration.ofSeconds(HEARTBEAT_TTL_SECONDS));
            } catch (Exception e) {
                log.warn("Heartbeat refresh failed: {}", e.getMessage());
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("Cluster heartbeat started (interval={}s, TTL={}s) for node={}",
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_TTL_SECONDS, selfNodeId);
    }

    private void subscribeBroadcast() {
        busService.subscribe(BROADCAST_CHANNEL, new ClusterBroadcastListener());
        log.info("Subscribed to broadcast channel: {}", BROADCAST_CHANNEL);
    }

    private class ClusterBroadcastListener implements MessageListener {
        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                var msg = (ClusterMessageRouter.BroadcastMessage) jsonSerializer.deserialize(
                        message.getBody(), ClusterMessageRouter.BroadcastMessage.class);
                if (msg == null || !selfNodeId.equals(msg.getTargetNodeId())) {
                    return;
                }
                Map<String, SocketIOClient> hubClients = clientRegistry.get(msg.getHubName());
                if (hubClients == null) {
                    return;
                }
                SocketIOClient client = hubClients.get(msg.getConnectionId());
                if (client != null && client.isChannelOpen()) {
                    if (msg.isBinary()) {
                        // Binary broadcast: deliver raw byte[] frame
                        client.sendEvent(msg.getMethod(), msg.getBinaryData());
                    } else {
                        client.sendEvent(msg.getMethod(), msg.getData());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to process broadcast message: {}", e.getMessage());
            }
        }
    }

    @Override
    public void onConnected(String connectionId, String hubName, String userId) {
        try {
            redisService.setWithTtl(CONN_KEY_PREFIX + connectionId, selfNodeId,
                    Duration.ofSeconds(connectionTtl));
            redisService.sadd(NODE_CONNS_PREFIX + selfNodeId + NODE_CONNS_SUFFIX, connectionId);
            redisService.sadd(USER_KEY_PREFIX + userId, connectionId);
        } catch (Exception e) {
            log.warn("Redis sync failed for onConnected: {}", connectionId, e);
        }
    }

    @Override
    public void onDisconnected(String connectionId, String hubName, String userId,
                               Set<String> groups) {
        try {
            // Guard: only delete keys owned by this node
            Object ownerObj = redisService.get(CONN_KEY_PREFIX + connectionId);
            if (ownerObj == null || !selfNodeId.equals(ownerObj.toString())) {
                return;
            }
            redisService.del(CONN_KEY_PREFIX + connectionId);
            redisService.del(CONN_KEY_PREFIX + connectionId + CONN_GROUPS_SUFFIX);
            if (groups != null) {
                for (String group : groups) {
                    redisService.srem(GROUP_KEY_PREFIX + group, connectionId);
                }
            }
            redisService.srem(NODE_CONNS_PREFIX + selfNodeId + NODE_CONNS_SUFFIX, connectionId);
            redisService.srem(USER_KEY_PREFIX + userId, connectionId);
        } catch (Exception e) {
            log.warn("Redis sync failed for onDisconnected: {}", connectionId, e);
        }
    }

    @Override
    public void onGroupChanged(String connectionId, String groupName, boolean joined) {
        try {
            if (joined) {
                redisService.sadd(CONN_KEY_PREFIX + connectionId + CONN_GROUPS_SUFFIX, groupName);
                redisService.sadd(GROUP_KEY_PREFIX + groupName, connectionId);
            } else {
                redisService.srem(CONN_KEY_PREFIX + connectionId + CONN_GROUPS_SUFFIX, groupName);
                redisService.srem(GROUP_KEY_PREFIX + groupName, connectionId);
            }
        } catch (Exception e) {
            log.warn("Redis sync failed for onGroupChanged: {} group={}", connectionId, groupName, e);
        }
    }

    @Override
    public void shutdown() {
        log.info("Cluster connection event handler shutting down...");
        heartbeatExecutor.shutdownNow();
        try {
            busService.unsubscribe(BROADCAST_CHANNEL, new ClusterBroadcastListener());
        } catch (Exception e) {
            log.warn("Failed to unsubscribe broadcast channel", e);
        }
        try {
            redisService.del(HEARTBEAT_KEY_PREFIX + selfNodeId + HEARTBEAT_SUFFIX);
            redisService.del(NODE_CONNS_PREFIX + selfNodeId + NODE_CONNS_SUFFIX);
        } catch (Exception e) {
            log.warn("Failed to clean up node keys", e);
        }
        log.info("Cluster connection event handler shutdown complete");
    }
}
