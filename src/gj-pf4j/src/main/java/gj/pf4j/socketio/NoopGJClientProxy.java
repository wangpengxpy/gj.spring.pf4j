/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import java.util.concurrent.CompletableFuture;

class NoopGJClientProxy implements GJClientProxy {

    @Override
    public CompletableFuture<Void> sendAsync(String method, Object data) {
        return CompletableFuture.completedFuture(null);
    }
}
