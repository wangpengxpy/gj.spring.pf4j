package gj.pf4j.socketio.cluster;

import com.corundumstudio.socketio.SocketIOClient;
import gj.pf4j.redis.IGJRedisBusService;
import gj.pf4j.redis.IGJRedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ClusterMessageRouter implements IMessageRouter {

    private static final Logger log = LoggerFactory.getLogger(ClusterMessageRouter.class);

    private static final String CONN_KEY_PREFIX = "socketio:conn:";
    private static final String HEARTBEAT_KEY_PREFIX = "socketio:node:";
    private static final String HEARTBEAT_SUFFIX = ":heartbeat";
    private static final String BROADCAST_CHANNEL = "socketio:broadcast";

    private final Map<String, Map<String, SocketIOClient>> clientRegistry;
    private final IGJRedisService redisService;
    private final IGJRedisBusService busService;
    private final String selfNodeId;

    public ClusterMessageRouter(Map<String, Map<String, SocketIOClient>> clientRegistry,
                         IGJRedisService redisService,
                         IGJRedisBusService busService,
                         String selfNodeId) {
        this.clientRegistry = clientRegistry;
        this.redisService = redisService;
        this.busService = busService;
        this.selfNodeId = selfNodeId;
    }

    @Override
    public boolean sendToConnection(String hubName, String connectionId,
                                    String method, Map<String, Object> message) {
        // 1. Try local delivery
        Map<String, SocketIOClient> hubClients = clientRegistry.get(hubName);
        if (hubClients != null) {
            SocketIOClient client = hubClients.get(connectionId);
            if (client != null && client.isChannelOpen()) {
                client.sendEvent(method, message);
                return true;
            }
        }

        // 2. Look up remote node
        Object nodeIdObj = redisService.get(CONN_KEY_PREFIX + connectionId);
        if (nodeIdObj == null) {
            return false;
        }
        String nodeId = nodeIdObj.toString();

        // 3. Self but local client gone — stale
        if (selfNodeId.equals(nodeId)) {
            return false;
        }

        // 4. Check target node heartbeat
        if (!redisService.exists(HEARTBEAT_KEY_PREFIX + nodeId + HEARTBEAT_SUFFIX)) {
            log.debug("Target node {} heartbeat lost, discarding message to {}", nodeId, connectionId);
            return false;
        }

        // 5. Publish cross-node broadcast
        try {
            var broadcastMsg = new BroadcastMessage(nodeId, hubName, connectionId, method, message);
            busService.publishAsync(BROADCAST_CHANNEL, broadcastMsg);
            return true;
        } catch (Exception e) {
            log.error("Failed to publish broadcast message for {}", connectionId, e);
            return false;
        }
    }

    public static class BroadcastMessage {
        private String targetNodeId;
        private String hubName;
        private String connectionId;
        private String method;
        private Map<String, Object> data;

        public BroadcastMessage() {}

        public BroadcastMessage(String targetNodeId, String hubName, String connectionId,
                                String method, Map<String, Object> data) {
            this.targetNodeId = targetNodeId;
            this.hubName = hubName;
            this.connectionId = connectionId;
            this.method = method;
            this.data = data;
        }

        public String getTargetNodeId() { return targetNodeId; }
        public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }

        public String getHubName() { return hubName; }
        public void setHubName(String hubName) { this.hubName = hubName; }

        public String getConnectionId() { return connectionId; }
        public void setConnectionId(String connectionId) { this.connectionId = connectionId; }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }

        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
    }
}
