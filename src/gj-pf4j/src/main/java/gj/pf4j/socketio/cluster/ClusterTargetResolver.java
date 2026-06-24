package gj.pf4j.socketio.cluster;

import com.corundumstudio.socketio.SocketIOClient;
import gj.pf4j.redis.IGJRedisService;

import java.util.*;

public class ClusterTargetResolver implements ITargetResolver {

    private static final String GROUP_KEY_PREFIX = "socketio:group:";
    private static final String USER_KEY_PREFIX = "socketio:user:";

    private final Map<String, Set<String>> groupConnections;
    private final Map<String, Set<String>> userConnections;
    private final Map<String, Map<String, SocketIOClient>> clientRegistry;
    private final IGJRedisService redisService;

    public ClusterTargetResolver(Map<String, Set<String>> groupConnections,
                          Map<String, Set<String>> userConnections,
                          Map<String, Map<String, SocketIOClient>> clientRegistry,
                          IGJRedisService redisService) {
        this.groupConnections = groupConnections;
        this.userConnections = userConnections;
        this.clientRegistry = clientRegistry;
        this.redisService = redisService;
    }

    @Override
    public Set<String> resolveTargets(String hubName,
                                      Collection<String> targetGroups,
                                      Collection<String> targetUserIds) {
        Set<String> result = new HashSet<>();

        if (targetGroups != null && !targetGroups.isEmpty()) {
            for (String group : targetGroups) {
                result.addAll(redisService.smembers(GROUP_KEY_PREFIX + group));
            }
        } else if (targetUserIds != null && !targetUserIds.isEmpty()) {
            for (String userId : targetUserIds) {
                result.addAll(redisService.smembers(USER_KEY_PREFIX + userId));
            }
        }

        // Remove connections that are already local
        result.removeIf(cid -> isLocal(hubName, cid));

        return result;
    }

    private boolean isLocal(String hubName, String connectionId) {
        Map<String, SocketIOClient> hubClients = clientRegistry.get(hubName);
        return hubClients != null && hubClients.containsKey(connectionId);
    }
}
