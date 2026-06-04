/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

public interface GJHubContext<THub extends GJHub>  {

    /**
     * Get client proxy for invoking methods on clients
     */
    GJHubCallerClients getClients();

    /**
     * Get group manager for managing named groups
     */
    GJGroupManager getGroups();
}