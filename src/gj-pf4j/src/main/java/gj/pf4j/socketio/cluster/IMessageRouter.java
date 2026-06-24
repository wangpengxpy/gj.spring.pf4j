package gj.pf4j.socketio.cluster;

import java.util.Map;

public interface IMessageRouter {

    boolean sendToConnection(String hubName, String connectionId,
                             String method, Map<String, Object> message);
}
