package gj.pf4j.socketio.cluster;

import com.corundumstudio.socketio.SocketIOClient;

import java.util.Map;

public class DefaultMessageRouter implements IMessageRouter {

    private final Map<String, Map<String, SocketIOClient>> clientRegistry;

    public DefaultMessageRouter(Map<String, Map<String, SocketIOClient>> clientRegistry) {
        this.clientRegistry = clientRegistry;
    }

    @Override
    public boolean sendToConnection(String hubName, String connectionId,
                                    String method, Map<String, Object> message) {
        Map<String, SocketIOClient> hubClients = clientRegistry.get(hubName);
        if (hubClients == null) {
            return false;
        }
        SocketIOClient client = hubClients.get(connectionId);
        if (client != null && client.isChannelOpen()) {
            client.sendEvent(method, message);
            return true;
        }
        return false;
    }

    @Override
    public boolean sendBinaryToConnection(String hubName, String connectionId,
                                          String eventName, byte[] data) {
        Map<String, SocketIOClient> hubClients = clientRegistry.get(hubName);
        if (hubClients == null) {
            return false;
        }
        SocketIOClient client = hubClients.get(connectionId);
        if (client != null && client.isChannelOpen()) {
            client.sendEvent(eventName, data);
            return true;
        }
        return false;
    }
}
