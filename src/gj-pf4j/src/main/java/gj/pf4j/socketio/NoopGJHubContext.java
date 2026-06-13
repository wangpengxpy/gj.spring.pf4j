/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

class NoopGJHubContext<T extends GJHub> implements GJHubContext<T> {

    private static final GJHubCallerClients NOOP_CLIENTS = new NoopGJHubCallerClients();
    private static final GJGroupManager NOOP_GROUPS = new NoopGJGroupManager();

    @Override
    public GJHubCallerClients getClients() {
        return NOOP_CLIENTS;
    }

    @Override
    public GJGroupManager getGroups() {
        return NOOP_GROUPS;
    }
}
