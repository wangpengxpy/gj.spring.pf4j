/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class GJExternalClientProxy implements GJClientProxy {
    private static final Logger log = LoggerFactory.getLogger(GJExternalClientProxy.class);

    private final String hubName;
    private final Collection<String> targetConnectionIds;
    private final Collection<String> targetGroups;
    private final Collection<String> excludedConnectionIds;
    private final boolean excludeCaller;
    private final Collection<String> targetUserIds;
    private final Collection<String> excludedUserIds;
    private final GJHubManager hubManager;

    public GJExternalClientProxy(String hubName,
                                 Collection<String> targetConnectionIds,
                                 Collection<String> targetGroups,
                                 Collection<String> excludedConnectionIds,
                                 boolean excludeCaller,
                                 Collection<String> targetUserIds,
                                 Collection<String> excludedUserIds,
                                 GJHubManager hubManager) {
        this.hubName = hubName;
        this.targetConnectionIds = targetConnectionIds != null ?
                Collections.unmodifiableCollection(targetConnectionIds) : null;
        this.targetGroups = targetGroups != null ?
                Collections.unmodifiableCollection(targetGroups) : null;
        this.excludedConnectionIds = excludedConnectionIds != null ?
                Collections.unmodifiableCollection(excludedConnectionIds) : null;
        this.excludeCaller = excludeCaller;
        this.targetUserIds = targetUserIds != null ?
                Collections.unmodifiableCollection(targetUserIds) : null;
        this.excludedUserIds = excludedUserIds != null ?
                Collections.unmodifiableCollection(excludedUserIds) : null;
        this.hubManager = hubManager;
    }

    // ================ Core Send Methods ================

    @Override
    public CompletableFuture<Void> sendAsync(String method, Object data) {
        log.debug("ExternalClientProxy[{}] - Sending message: {} to targets: {}",
                hubName, method, getTargetDescription());
        if (data == null) {
            return CompletableFuture.completedFuture(null);
        }
        return hubManager.sendMessageAsync(
                hubName,
                method,
                data,
                targetConnectionIds,
                targetGroups,
                excludedConnectionIds,
                targetUserIds,
                excludedUserIds
        ).thenAccept(result -> {
            log.debug("ExternalClientProxy[{}] - Message sent successfully method={}, count={}", hubName, method, result);
        }).exceptionally(throwable -> {
            log.error("ExternalClientProxy[{}] - Message sent failed method={}, exception:{}", hubName, method, throwable.getMessage());
            return null;
        });
    }

    @Override
    public void send(String method, Object data) {
        if (data == null) {
            return;
        }
        try {
            hubManager.sendMessage(
                    hubName,
                    method,
                    data,
                    targetConnectionIds,
                    targetGroups,
                    excludedConnectionIds,
                    targetUserIds,
                    excludedUserIds
            );
            log.debug("ExternalClientProxy[{}] - Message sent successfully: {}", hubName, method);
        } catch (Exception e) {
            log.error("ExternalClientProxy[{}] - Synchronous send failed method={}", hubName, method, e);
        }
    }

    @Override
    public CompletableFuture<Void> sendBinaryAsync(String eventName, byte[] data) {
        log.debug("ExternalClientProxy[{}] - Sending binary: {} to targets: {}",
                hubName, eventName, getTargetDescription());
        if (data == null) {
            return CompletableFuture.completedFuture(null);
        }
        return hubManager.sendBinaryAsync(
                hubName,
                eventName,
                data,
                targetConnectionIds,
                targetGroups,
                excludedConnectionIds,
                targetUserIds,
                excludedUserIds
        ).thenAccept(result -> {
            log.debug("ExternalClientProxy[{}] - Binary sent successfully event={}, count={}",
                    hubName, eventName, result);
        }).exceptionally(throwable -> {
            log.error("ExternalClientProxy[{}] - Binary send failed event={}, exception:{}",
                    hubName, eventName, throwable.getMessage());
            return null;
        });
    }

    // ================ Helper Methods ================

    private String getTargetDescription() {
        List<String> parts = new ArrayList<>();

        if (targetConnectionIds != null && !targetConnectionIds.isEmpty()) {
            parts.add("connections(" + targetConnectionIds.size() + ")");
        }

        if (targetGroups != null && !targetGroups.isEmpty()) {
            parts.add("groups(" + targetGroups.size() + ")");
        }

        if (targetUserIds != null && !targetUserIds.isEmpty()) {
            parts.add("users(" + targetUserIds.size() + ")");
        }

        if (excludedConnectionIds != null && !excludedConnectionIds.isEmpty()) {
            parts.add("excludeConnections(" + excludedConnectionIds.size() + ")");
        }

        if (excludedUserIds != null && !excludedUserIds.isEmpty()) {
            parts.add("excludeUsers(" + excludedUserIds.size() + ")");
        }

        if (parts.isEmpty()) {
            return "all";
        }

        return String.join(" + ", parts);
    }

    private String getTargetType() {
        if (targetConnectionIds != null && !targetConnectionIds.isEmpty()) {
            return "specific_connections";
        } else if (targetGroups != null && !targetGroups.isEmpty()) {
            return "groups";
        } else if (targetUserIds != null && !targetUserIds.isEmpty()) {
            return "users";
        } else if (excludedConnectionIds != null && !excludedConnectionIds.isEmpty()) {
            return "all_except_connections";
        } else if (excludedUserIds != null && !excludedUserIds.isEmpty()) {
            return "all_except_users";
        } else {
            return "all";
        }
    }

    // ================ Chained Call Support ================

    /**
     * Chained call: execute callback after sending message
     */
    public CompletableFuture<Void> sendAsyncThen(String method, Object[] args, Runnable callback) {
        return sendAsync(method, args).thenRun(callback);
    }

    /**
     * Chained call: execute callback after sending message (with result)
     */
    public <T> CompletableFuture<T> sendAsyncThen(String method, Object[] args, Supplier<T> supplier) {
        return sendAsync(method, args).thenApply(v -> supplier.get());
    }

    // ================ Get Target Info ================

    /**
     * Get target connection IDs (if specified)
     */
    public Optional<Collection<String>> getTargetConnectionIds() {
        return Optional.ofNullable(targetConnectionIds);
    }

    /**
     * Get target groups (if specified)
     */
    public Optional<Collection<String>> getTargetGroups() {
        return Optional.ofNullable(targetGroups);
    }

    /**
     * Get target users (if specified)
     */
    public Optional<Collection<String>> getTargetUserIds() {
        return Optional.ofNullable(targetUserIds);
    }

    public boolean isExcludeCaller() {
        return excludeCaller;
    }
}