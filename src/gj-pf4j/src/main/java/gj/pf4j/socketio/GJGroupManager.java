/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import java.util.Collection;

public interface GJGroupManager {

    /**
     * Add the current connection to the specified group (room)
     *
     * @param groupName Group name
     * @return CompletableFuture async operation result
     */
    CompletableFuture<Void> addToGroupAsync(String groupName);

    /**
     * Add the specified connection to the specified group (room)
     *
     * @param connectionId Connection ID
     * @param groupName    Group name
     * @return CompletableFuture async operation result
     */
    CompletableFuture<Void> addToGroupAsync(String connectionId, String groupName);

    /**
     * Remove the current connection from the specified group (room)
     *
     * @param groupName Group name
     * @return CompletableFuture async operation result
     */
    CompletableFuture<Void> removeFromGroupAsync(String groupName);

    /**
     * Remove the specified connection from the specified group (room)
     *
     * @param connectionId Connection ID
     * @param groupName    Group name
     * @return CompletableFuture async operation result
     */
    CompletableFuture<Void> removeFromGroupAsync(String connectionId, String groupName);

    /**
     * Check if the current connection is in the specified group
     *
     * @param groupName Group name
     * @return CompletableFuture returns true if the connection is in the group
     */
    CompletableFuture<Boolean> isInGroupAsync(String groupName);

    /**
     * Check if the specified connection is in the specified group
     *
     * @param connectionId Connection ID
     * @param groupName    Group name
     * @return CompletableFuture returns true if the connection is in the group
     */
    CompletableFuture<Boolean> isInGroupAsync(String connectionId, String groupName);

    /**
     * Get all groups that the current connection belongs to
     *
     * @return CompletableFuture returns a set of group names
     */
    CompletableFuture<Set<String>> getGroupsForConnectionAsync();

    /**
     * Get all groups that the specified connection belongs to
     *
     * @param connectionId Connection ID
     * @return CompletableFuture returns a set of group names
     */
    CompletableFuture<Set<String>> getGroupsForConnectionAsync(String connectionId);

    /**
     * Get all connection IDs in the specified group
     *
     * @param groupName Group name
     * @return CompletableFuture returns a set of connection IDs
     */
    CompletableFuture<Set<String>> getConnectionsInGroupAsync(String groupName);

    /**
     * Add the current connection to multiple groups
     *
     * @param groupNames Collection of group names
     * @return CompletableFuture async operation result
     */
    default CompletableFuture<Void> addToGroupsAsync(Collection<String> groupNames) {
        if (groupNames == null || groupNames.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = groupNames.stream()
                .map(this::addToGroupAsync)
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Remove the current connection from multiple groups
     *
     * @param groupNames Collection of group names
     * @return CompletableFuture async operation result
     */
    default CompletableFuture<Void> removeFromGroupsAsync(Collection<String> groupNames) {
        if (groupNames == null || groupNames.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = groupNames.stream()
                .map(this::removeFromGroupAsync)
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Remove the current connection from all groups
     *
     * @return CompletableFuture async operation result
     */
    default CompletableFuture<Void> removeFromAllGroupsAsync() {
        return getGroupsForConnectionAsync()
                .thenCompose(this::removeFromGroupsAsync);
    }

    /**
     * Synchronous version: add the current connection to the specified group
     *
     * @param groupName Group name
     */
    default void addToGroup(String groupName) {
        addToGroupAsync(groupName).join();
    }

    /**
     * Synchronous version: remove the current connection from the specified group
     *
     * @param groupName Group name
     */
    default void removeFromGroup(String groupName) {
        removeFromGroupAsync(groupName).join();
    }

    /**
     * Synchronous version: check if the current connection is in the specified group
     *
     * @param groupName Group name
     * @return returns true if the connection is in the group
     */
    default boolean isInGroup(String groupName) {
        return isInGroupAsync(groupName).join();
    }

    /**
     * Synchronous version: get all groups that the current connection belongs to
     *
     * @return returns a set of group names
     */
    default Set<String> getGroupsForConnection() {
        return getGroupsForConnectionAsync().join();
    }

    /**
     * Synchronous version: get all connection IDs in the specified group
     *
     * @param groupName Group name
     * @return returns a set of connection IDs
     */
    default Set<String> getConnectionsInGroup(String groupName) {
        return getConnectionsInGroupAsync(groupName).join();
    }

    /**
     * Check if a group exists
     *
     * @param groupName Group name
     * @return CompletableFuture returns true if the group exists
     */
    default CompletableFuture<Boolean> groupExistsAsync(String groupName) {
        return getConnectionsInGroupAsync(groupName)
                .thenApply(connections -> !connections.isEmpty())
                .exceptionally(ex -> false);
    }

    /**
     * Get the number of connections in a group
     *
     * @param groupName Group name
     * @return CompletableFuture returns the number of connections
     */
    default CompletableFuture<Integer> getGroupSizeAsync(String groupName) {
        return getConnectionsInGroupAsync(groupName)
                .thenApply(Set::size);
    }

    /**
     * Switch the current connection to a new group (leave old group, then join new group)
     *
     * @param oldGroupName Old group name
     * @param newGroupName New group name
     * @return CompletableFuture async operation result
     */
    default CompletableFuture<Void> switchGroupAsync(String oldGroupName, String newGroupName) {
        return removeFromGroupAsync(oldGroupName)
                .thenCompose(v -> addToGroupAsync(newGroupName));
    }
}
