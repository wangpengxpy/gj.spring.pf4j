/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import com.corundumstudio.socketio.SocketIOClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

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
