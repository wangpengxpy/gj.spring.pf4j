package gj.pf4j.socketio.cluster;

import java.util.Set;

public interface IConnectionEventHandler {

    void onConnected(String connectionId, String hubName, String userId);

    void onDisconnected(String connectionId, String hubName, String userId,
                        Set<String> groups);

    void onGroupChanged(String connectionId, String groupName, boolean joined);

    void shutdown();
}
