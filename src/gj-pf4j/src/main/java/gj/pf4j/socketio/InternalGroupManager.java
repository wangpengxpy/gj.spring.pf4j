/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import com.corundumstudio.socketio.SocketIOClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

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
