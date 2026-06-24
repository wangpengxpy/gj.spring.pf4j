package gj.pf4j.socketio.cluster;

import java.util.Set;

public class NoopConnectionEventHandler implements IConnectionEventHandler {

    @Override
    public void onConnected(String connectionId, String hubName, String userId) {
        // no-op in monolith mode
    }

    @Override
    public void onDisconnected(String connectionId, String hubName, String userId,
                               Set<String> groups) {
        // no-op in monolith mode
    }

    @Override
    public void onGroupChanged(String connectionId, String groupName, boolean joined) {
        // no-op in monolith mode
    }

    @Override
    public void shutdown() {
        // no-op in monolith mode
    }
}
