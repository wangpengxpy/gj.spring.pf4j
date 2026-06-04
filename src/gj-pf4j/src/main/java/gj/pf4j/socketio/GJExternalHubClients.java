/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import java.util.Collection;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GJExternalHubClients implements GJHubCallerClients {
    private static final Logger log = LoggerFactory.getLogger(GJExternalHubClients.class);

    private final String hubName;
    private final GJHubManager hubManager;

    public GJExternalHubClients(String hubName, GJHubManager hubManager) {
        this.hubName = hubName;
        this.hubManager = hubManager;
    }

    // ================ Core Method Implementation ================

    @Override
    public GJClientProxy all() {
        log.debug("ExternalHubClients[{}] - Creating proxy for ALL clients", hubName);
        return new GJExternalClientProxy(
                hubName,
                null,            // all connections
                null,            // all groups
                null,            // exclude no connections
                false,           // exclude no caller
                null,            // all users
                null,            // exclude no users
                hubManager
        );
    }

    @Override
    public GJClientProxy caller() {
        throw new UnsupportedOperationException(
                "ExternalHubClients cannot access 'caller' - no current connection context available. " +
                        "Use client(connectionId) for specific connections.");
    }

    @Override
    public GJClientProxy others() {
        throw new UnsupportedOperationException(
                "ExternalHubClients cannot access 'others' - no current connection context available. " +
                        "Use all() for all connections or specific targeting methods.");
    }

    @Override
    public GJClientProxy client(String connectionId) {
        log.debug("ExternalHubClients[{}] - Creating proxy for client: {}", hubName, connectionId);
        return new GJExternalClientProxy(
                hubName,
                Collections.singleton(connectionId),  // specified connection
                null,                                 // no groups
                null,                                 // exclude no connections
                false,                                // exclude no caller
                null,                                 // no users
                null,                                 // exclude no users
                hubManager
        );
    }

    @Override
    public GJClientProxy clients(Collection<String> connectionIds) {
        log.debug("ExternalHubClients[{}] - Creating proxy for {} clients", hubName, connectionIds.size());
        return new GJExternalClientProxy(
                hubName,
                connectionIds,    // multiple specified connections
                null,             // no groups
                null,             // exclude no connections
                false,            // exclude no caller
                null,             // no users
                null,             // exclude no users
                hubManager
        );
    }

    @Override
    public GJClientProxy group(String groupName) {
        log.debug("ExternalHubClients[{}] - Creating proxy for group: {}", hubName, groupName);
        return new GJExternalClientProxy(
                hubName,
                null,                                 // no direct connections
                Collections.singleton(groupName),     // specified group
                null,                                 // exclude no connections
                false,                                // exclude no caller
                null,                                 // no users
                null,                                 // exclude no users
                hubManager
        );
    }

    @Override
    public GJClientProxy groups(Collection<String> groupNames) {
        log.debug("ExternalHubClients[{}] - Creating proxy for {} groups", hubName, groupNames.size());
        return new GJExternalClientProxy(
                hubName,
                null,             // no direct connections
                groupNames,       // multiple specified groups
                null,             // exclude no connections
                false,            // exclude no caller
                null,             // no users
                null,             // exclude no users
                hubManager
        );
    }

    @Override
    public GJClientProxy othersInGroup(String groupName) {
        log.debug("ExternalHubClients[{}] - Creating proxy for others in group: {}", hubName, groupName);
        throw new UnsupportedOperationException(
                "ExternalHubClients cannot access 'othersInGroup' - no current connection context available. " +
                        "Use group(groupName) for the entire group.");
    }

    @Override
    public GJClientProxy othersInGroups(Collection<String> groupNames) {
        throw new UnsupportedOperationException(
                "ExternalHubClients cannot access 'othersInGroups' - no current connection context available. " +
                        "Use groups(groupNames) for the entire groups.");
    }

    @Override
    public GJClientProxy user(String userId) {
        log.debug("ExternalHubClients[{}] - Creating proxy for user: {}", hubName, userId);
        return new GJExternalClientProxy(
                hubName,
                null,                                 // no direct connections
                null,                                 // no groups
                null,                                 // exclude no connections
                false,                                // exclude no caller
                Collections.singleton(userId),        // specified user
                null,                                 // exclude no users
                hubManager
        );
    }

    @Override
    public GJClientProxy users(Collection<String> userIds) {
        log.debug("ExternalHubClients[{}] - Creating proxy for {} users", hubName, userIds.size());
        return new GJExternalClientProxy(
                hubName,
                null,             // no direct connections
                null,             // no groups
                null,             // exclude no connections
                false,            // exclude no caller
                userIds,          // multiple specified users
                null,             // exclude no users
                hubManager
        );
    }

    @Override
    public GJClientProxy groupExceptUser(String groupName, String userId) {
        log.debug("ExternalHubClients[{}] - Creating proxy for group {} except user {}", hubName, groupName, userId);
        return new GJExternalClientProxy(
                hubName,
                null,                                 // no direct connections
                Collections.singleton(groupName),     // specified group
                null,                                 // exclude no connections
                false,                                // exclude no caller
                null,                                 // no target users
                Collections.singleton(userId),        // exclude specified user
                hubManager
        );
    }

    @Override
    public GJClientProxy allExcept(Collection<String> excludedConnectionIds) {
        log.debug("ExternalHubClients[{}] - Creating proxy for all except {} connections",
                hubName, excludedConnectionIds.size());
        return new GJExternalClientProxy(
                hubName,
                null,             // all connections
                null,             // no groups
                excludedConnectionIds,  // excluded connections
                false,            // exclude no caller
                null,             // no users
                null,             // exclude no users
                hubManager
        );
    }

    @Override
    public GJClientProxy groupExceptUsers(String groupName, Collection<String> excludedUserIds) {
        log.debug("ExternalHubClients[{}] - Creating proxy for group {} except {} users",
                hubName, groupName, excludedUserIds.size());
        return new GJExternalClientProxy(
                hubName,
                null,                     // no direct connections
                Collections.singleton(groupName),  // specified group
                null,                     // exclude no connections
                false,                    // exclude no caller
                null,                     // no target users
                excludedUserIds,          // excluded users
                hubManager
        );
    }
}