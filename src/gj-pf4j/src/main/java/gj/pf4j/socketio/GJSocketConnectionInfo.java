/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import com.corundumstudio.socketio.SocketIOClient;

public class GJSocketConnectionInfo {
    private final String connectionId;
    private final String hubName;
    private final String userId;
    private final SocketIOClient client;
    private final long connectTime;
    private volatile long lastHeartbeat;

    public GJSocketConnectionInfo(String connectionId, String hubName, String userId, SocketIOClient client) {
        this.connectionId = connectionId;
        this.hubName = hubName;
        this.userId = userId;
        this.client = client;
        this.connectTime = System.currentTimeMillis();
        this.lastHeartbeat = this.connectTime;
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getHubName() {
        return hubName;
    }

    public String getUserId() {
        return userId;
    }

    public SocketIOClient getClient() {
        return client;
    }

    public long getConnectTime() {
        return connectTime;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void updateLastHeartbeat() {
        this.lastHeartbeat = System.currentTimeMillis();
    }

    public boolean isStale(long timeoutMs) {
        return System.currentTimeMillis() - lastHeartbeat > timeoutMs;
    }

    @Override
    public String toString() {
        return String.format("ConnectionInfo{id=%s, hub=%s, user=%s, connected=%d}",
                connectionId, hubName, userId, connectTime);
    }
}