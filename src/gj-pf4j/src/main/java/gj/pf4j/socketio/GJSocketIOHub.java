/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import java.util.concurrent.CompletableFuture;

public interface GJSocketIOHub {
    String getHubName();
    CompletableFuture<Void> onConnectedAsync();
    CompletableFuture<Void> onDisconnectedAsync();
}