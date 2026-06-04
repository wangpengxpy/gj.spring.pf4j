/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

public class GJGenericHubContext<T extends GJHub> implements GJHubContext<T> {
    private final GJHubCallerClients clients;
    private final GJExternalGroupManager groups;

    public GJGenericHubContext(String hubName, GJHubManager hubManager) {
        this.clients = new GJExternalHubClients(hubName, hubManager);
        this.groups = new GJExternalGroupManager(hubName, hubManager);
    }

    @Override
    public GJHubCallerClients getClients() {
        return clients;
    }

    @Override
    public GJGroupManager getGroups() {
        return groups;
    }
}