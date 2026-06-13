/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import java.util.Collection;

class NoopGJHubCallerClients implements GJHubCallerClients {

    private static final GJClientProxy NOOP = new NoopGJClientProxy();

    @Override
    public GJClientProxy all() {
        return NOOP;
    }

    @Override
    public GJClientProxy caller() {
        return NOOP;
    }

    @Override
    public GJClientProxy others() {
        return NOOP;
    }

    @Override
    public GJClientProxy client(String connectionId) {
        return NOOP;
    }

    @Override
    public GJClientProxy clients(Collection<String> connectionIds) {
        return NOOP;
    }

    @Override
    public GJClientProxy group(String groupName) {
        return NOOP;
    }

    @Override
    public GJClientProxy groups(Collection<String> groupNames) {
        return NOOP;
    }

    @Override
    public GJClientProxy othersInGroup(String groupName) {
        return NOOP;
    }

    @Override
    public GJClientProxy othersInGroups(Collection<String> groupNames) {
        return NOOP;
    }

    @Override
    public GJClientProxy user(String userId) {
        return NOOP;
    }

    @Override
    public GJClientProxy users(Collection<String> userIds) {
        return NOOP;
    }

    @Override
    public GJClientProxy groupExceptUser(String groupName, String userId) {
        return NOOP;
    }

    @Override
    public GJClientProxy allExcept(Collection<String> excludedConnectionIds) {
        return NOOP;
    }

    @Override
    public GJClientProxy groupExceptUsers(String groupName, Collection<String> excludedUserIds) {
        return NOOP;
    }
}
