/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import java.util.Collection;

public interface GJHubCallerClients {

    /**
     * Get all clients
     */
    GJClientProxy all();

    /**
     * Get the caller client (current connection)
     */
    GJClientProxy caller();

    /**
     * Get all other clients (except the caller)
     */
    GJClientProxy others();

    /**
     * Get the specified client
     */
    GJClientProxy client(String connectionId);

    /**
     * Get multiple specified clients
     */
    GJClientProxy clients(Collection<String> connectionIds);

    /**
     * Get all clients in the specified group
     */
    GJClientProxy group(String groupName);

    /**
     * Get all clients in multiple specified groups
     */
    GJClientProxy groups(Collection<String> groupNames);

    /**
     * Get other clients in the specified group (excluding the caller)
     */
    GJClientProxy othersInGroup(String groupName);

    /**
     * Get other clients in multiple specified groups (excluding the caller)
     */
    GJClientProxy othersInGroups(Collection<String> groupNames);

    /**
     * Get proxy for the specified user
     * Can send messages to all clients of the specified user (a user may be connected from multiple devices)
     *
     * @param userId User ID
     */
    GJClientProxy user(String userId);

    /**
     * Get proxy for multiple specified users
     * Can send messages to all clients of multiple users
     *
     * @param userIds Collection of user IDs
     */
    GJClientProxy users(Collection<String> userIds);

    /**
     * Get proxy for the specified user in the specified group
     * Can send messages to all clients of the specified user in the specified group
     *
     * @param groupName Group name
     * @param userId    User ID
     */
    GJClientProxy groupExceptUser(String groupName, String userId);

    /**
     * Get all clients in the current connection (except the specified connection IDs)
     *
     * @param excludedConnectionIds Collection of connection IDs to exclude
     */
    GJClientProxy allExcept(Collection<String> excludedConnectionIds);

    /**
     * Get all clients in the specified group except the specified users
     *
     * @param groupName       Group name
     * @param excludedUserIds Collection of user IDs to exclude
     */
    GJClientProxy groupExceptUsers(String groupName, Collection<String> excludedUserIds);
}