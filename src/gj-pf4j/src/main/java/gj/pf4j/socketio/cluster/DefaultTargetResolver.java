package gj.pf4j.socketio.cluster;

import com.corundumstudio.socketio.SocketIOClient;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class DefaultTargetResolver implements ITargetResolver {

    public DefaultTargetResolver(Map<String, Set<String>> groupConnections,
                          Map<String, Set<String>> userConnections,
                          Map<String, Map<String, SocketIOClient>> clientRegistry) {
        // unused in default mode — all targets are local
    }

    @Override
    public Set<String> resolveTargets(String hubName,
                                      Collection<String> targetGroups,
                                      Collection<String> targetUserIds) {
        return Collections.emptySet();
    }
}
