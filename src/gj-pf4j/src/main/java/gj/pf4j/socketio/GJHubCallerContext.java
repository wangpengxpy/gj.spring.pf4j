/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GJHubCallerContext {
    private final String connectionId;
    private final String userId;
    private final Map<String, String> queryParams;
    private final Map<String, Object> items;

    public GJHubCallerContext(String connectionId, String userId, Map<String, String> queryParams) {
        this.userId = userId;
        this.connectionId = connectionId;
        this.queryParams = queryParams != null ? new HashMap<>(queryParams) : new HashMap<>();
        this.items = new ConcurrentHashMap<>();
    }

    public String getConnectionId() {
        return connectionId;
    }

    public String getQueryParam(String key) {
        return queryParams.get(key);
    }

    public Map<String, String> getQueryParams() {
        return Collections.unmodifiableMap(queryParams);
    }

    public String getUserId() {
        return userId;
    }

    public String getHubName() {
        return queryParams.get("hub");
    }

    public void setItem(String key, Object value) {
        items.put(key, value);
    }

    public Object getItem(String key) {
        return items.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getItem(String key, Class<T> type) {
        Object value = items.get(key);
        return type.isInstance(value) ? (T) value : null;
    }
}