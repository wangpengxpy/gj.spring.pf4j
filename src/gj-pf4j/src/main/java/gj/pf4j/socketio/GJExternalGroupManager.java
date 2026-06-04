/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import java.util.Collection;

public class GJExternalGroupManager implements GJGroupManager {
    private static final Logger log = LoggerFactory.getLogger(GJExternalGroupManager.class);

    private final String hubName;
    private final GJHubManager hubManager;

    public GJExternalGroupManager(String hubName, GJHubManager hubManager) {
        this.hubName = hubName;
        this.hubManager = hubManager;
    }

    @Override
    public CompletableFuture<Void> addToGroupAsync(String groupName) {
        log.warn("ExternalGroupManager cannot add current connection to group '{}' - " +
                "no current connection context available. " +
                "Use addToGroupAsync(connectionId, groupName) instead.", groupName);

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> addToGroupAsync(String connectionId, String groupName) {
        log.debug("ExternalGroupManager[{}] - Adding connection {} to group {}",
                hubName, connectionId, groupName);

        return hubManager.addConnectionToGroupAsync(hubName, connectionId, groupName)
                .thenApply(success -> {
                    if (!success) {
                        throw new RuntimeException(
                                String.format("Failed to add connection %s to group %s in hub %s",
                                        connectionId, groupName, hubName));
                    }
                    return null;
                });
    }

    @Override
    public CompletableFuture<Void> removeFromGroupAsync(String groupName) {
        log.warn("ExternalGroupManager cannot remove current connection from group '{}' - " +
                "no current connection context available. " +
                "Use removeFromGroupAsync(connectionId, groupName) instead.", groupName);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> removeFromGroupAsync(String connectionId, String groupName) {
        log.debug("ExternalGroupManager[{}] - Removing connection {} from group {}",
                hubName, connectionId, groupName);

        return hubManager.removeConnectionFromGroupAsync(hubName, connectionId, groupName)
                .thenApply(success -> {
                    if (!success) {
                        throw new RuntimeException(
                                String.format("Failed to remove connection %s from group %s in hub %s",
                                        connectionId, groupName, hubName));
                    }
                    return null;
                });
    }

    @Override
    public CompletableFuture<Boolean> isInGroupAsync(String groupName) {
        log.warn("ExternalGroupManager cannot check if current connection is in group '{}' - " +
                "no current connection context available. " +
                "Use isInGroupAsync(connectionId, groupName) instead.", groupName);
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> isInGroupAsync(String connectionId, String groupName) {
        log.debug("ExternalGroupManager[{}] - Checking if connection {} is in group {}",
                hubName, connectionId, groupName);
        return hubManager.isInGroupAsync(connectionId, groupName)
                .thenApply(success -> {
                    if (!success) {
                        throw new RuntimeException(
                                String.format("Failed to query isInGroup connection %s from group %s in hub %s",
                                        connectionId, groupName, hubName));
                    }
                    return null;
                });
    }

    @Override
    public CompletableFuture<Set<String>> getGroupsForConnectionAsync() {
        log.warn("ExternalGroupManager cannot get groups for current connection - " +
                "no current connection context available. " +
                "Use getGroupsForConnectionAsync(connectionId) instead.");
        return CompletableFuture.completedFuture(Collections.emptySet());
    }

    @Override
    public CompletableFuture<Set<String>> getGroupsForConnectionAsync(String connectionId) {
        log.debug("ExternalGroupManager[{}] - Getting groups for connection {}", hubName, connectionId);
        return hubManager.getGroupsForConnectionAsync(connectionId);
    }

    @Override
    public CompletableFuture<Set<String>> getConnectionsInGroupAsync(String groupName) {
        log.debug("ExternalGroupManager[{}] - Getting connections in group {}", hubName, groupName);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return hubManager.getConnectionIdsInGroup(groupName);
            } catch (Exception e) {
                log.error("Failed to get connections in group {} for hub {}", groupName, hubName, e);
                return Collections.emptySet();
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> groupExistsAsync(String groupName) {
        return getConnectionsInGroupAsync(groupName)
                .thenApply(connections -> !connections.isEmpty());
    }

    @Override
    public CompletableFuture<Integer> getGroupSizeAsync(String groupName) {
        return getConnectionsInGroupAsync(groupName)
                .thenApply(Set::size);
    }

    // ================ Extended Features ================

    /**
     * Batch add multiple connections to a group
     */
    public CompletableFuture<Void> addConnectionsToGroupAsync(Collection<String> connectionIds, String groupName) {
        log.debug("ExternalGroupManager[{}] - Adding {} connections to group {}",
                hubName, connectionIds.size(), groupName);

        List<CompletableFuture<Void>> futures = connectionIds.stream()
                .map(connectionId -> addToGroupAsync(connectionId, groupName))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Batch remove multiple connections from a group
     */
    public CompletableFuture<Void> removeConnectionsFromGroupAsync(Collection<String> connectionIds, String groupName) {
        log.debug("ExternalGroupManager[{}] - Removing {} connections from group {}",
                hubName, connectionIds.size(), groupName);
        List<CompletableFuture<Void>> futures = connectionIds.stream()
                .map(connectionId -> removeFromGroupAsync(connectionId, groupName))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Clear a group (remove all connections from the group)
     */
    public CompletableFuture<Void> clearGroupAsync(String groupName) {
        return getConnectionsInGroupAsync(groupName)
                .thenCompose(connectionIds -> removeConnectionsFromGroupAsync(connectionIds, groupName));
    }
}