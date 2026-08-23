package gj.pf4j.socketio.cluster;

import java.util.Map;

public interface IMessageRouter {

    boolean sendToConnection(String hubName, String connectionId,
                             String method, Map<String, Object> message);

    /**
     * Send a raw binary payload to a connection. Default no-op (returns false);
     * local and cluster routers override it.
     */
    default boolean sendBinaryToConnection(String hubName, String connectionId,
                                           String eventName, byte[] data) {
        return false;
    }
}
