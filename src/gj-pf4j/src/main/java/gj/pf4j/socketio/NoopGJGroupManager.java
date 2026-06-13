/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

class NoopGJGroupManager implements GJGroupManager {

    @Override
    public CompletableFuture<Void> addToGroupAsync(String groupName) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> addToGroupAsync(String connectionId, String groupName) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> removeFromGroupAsync(String groupName) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> removeFromGroupAsync(String connectionId, String groupName) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Boolean> isInGroupAsync(String groupName) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Boolean> isInGroupAsync(String connectionId, String groupName) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public CompletableFuture<Set<String>> getGroupsForConnectionAsync() {
        return CompletableFuture.completedFuture(Collections.emptySet());
    }

    @Override
    public CompletableFuture<Set<String>> getGroupsForConnectionAsync(String connectionId) {
        return CompletableFuture.completedFuture(Collections.emptySet());
    }

    @Override
    public CompletableFuture<Set<String>> getConnectionsInGroupAsync(String groupName) {
        return CompletableFuture.completedFuture(Collections.emptySet());
    }
}
