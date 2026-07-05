/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import com.corundumstudio.socketio.SocketIOClient;

import java.util.Map;

public class GJHubConnectionContext {
    private final String connectionId;
    private final String userId;
    private final SocketIOClient client;
    private final GJHubCallerContext hubCallerContext;
    private volatile long lastActivityTime;

    public GJHubConnectionContext(String connectionId, String userId,
                                  SocketIOClient client, Map<String, String> queryParams) {
        this.connectionId = connectionId;
        this.userId = userId;
        this.client = client;
        this.hubCallerContext = new GJHubCallerContext(connectionId, userId, queryParams);
        this.lastActivityTime = System.currentTimeMillis();
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getUserId() {
        return userId;
    }

    public SocketIOClient getClient() {
        return client;
    }

    public GJHubCallerContext getHubCallerContext() {
        return hubCallerContext;
    }

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    public void updateLastActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }
}
