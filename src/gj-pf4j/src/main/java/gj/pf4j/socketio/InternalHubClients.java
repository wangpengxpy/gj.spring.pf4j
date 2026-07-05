/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import com.corundumstudio.socketio.SocketIOClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ExecutorService;

class InternalHubClients implements GJHubCallerClients {
    private static final Logger log = LoggerFactory.getLogger(InternalHubClients.class);

    private final String currentConnectionId;
    private final String currentUserId;
    private final SocketIOClient currentClient;
    private final String hubName;
    private final GJHubManager hubManager;
    private final ExecutorService asyncExecutor;

    public InternalHubClients(String currentConnectionId, String currentUserId,
                              SocketIOClient currentClient, String hubName,
                              GJHubManager hubManager, ExecutorService asyncExecutor) {
        this.currentConnectionId = currentConnectionId;
        this.currentUserId = currentUserId;
        this.currentClient = currentClient;
        this.hubName = hubName;
        this.hubManager = hubManager;
        this.asyncExecutor = asyncExecutor;
    }

    @Override
    public GJClientProxy all() {
        return new InternalClientProxy(hubName, null, null, null, false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy caller() {
        return new InternalClientProxy(hubName,
                Collections.singleton(currentConnectionId), null, null, false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy others() {
        return new InternalClientProxy(hubName,
                null, null, Collections.singleton(currentConnectionId), false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy client(String connectionId) {
        return new InternalClientProxy(hubName,
                Collections.singleton(connectionId), null, null, false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy clients(Collection<String> connectionIds) {
        return new InternalClientProxy(hubName,
                connectionIds, null, null, false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy group(String groupName) {
        return new InternalClientProxy(hubName,
                null, Collections.singleton(groupName), null, false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy groups(Collection<String> groupNames) {
        return new InternalClientProxy(hubName,
                null, groupNames, null, false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy othersInGroup(String groupName) {
        return new InternalClientProxy(hubName,
                null, Collections.singleton(groupName),
                Collections.singleton(currentConnectionId), false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy othersInGroups(Collection<String> groupNames) {
        return new InternalClientProxy(hubName,
                null, groupNames, Collections.singleton(currentConnectionId), false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy user(String userId) {
        return new InternalClientProxy(hubName,
                null, null, null, false, Collections.singleton(userId), null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy users(Collection<String> userIds) {
        return new InternalClientProxy(hubName,
                null, null, null, false, userIds, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy groupExceptUser(String groupName, String userId) {
        return new InternalClientProxy(hubName,
                null, Collections.singleton(groupName), null, false, null,
                Collections.singleton(userId), hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy allExcept(Collection<String> excludedConnectionIds) {
        return new InternalClientProxy(hubName,
                null, null, excludedConnectionIds, false, null, null, hubManager, asyncExecutor);
    }

    @Override
    public GJClientProxy groupExceptUsers(String groupName, Collection<String> excludedUserIds) {
        return new InternalClientProxy(hubName,
                null, Collections.singleton(groupName), null, false, null, excludedUserIds, hubManager, asyncExecutor);
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public SocketIOClient getCurrentClient() {
        return currentClient;
    }
}
