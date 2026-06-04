/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import java.util.concurrent.CompletableFuture;

public interface GJClientProxy {

    /**
     * Async send message to client
     */
    CompletableFuture<Void> sendAsync(String method, Object data);

    /**
     * Async send message to client
     */
    default void send(String method, Object data) {
        sendAsync(method, data);
    }
}