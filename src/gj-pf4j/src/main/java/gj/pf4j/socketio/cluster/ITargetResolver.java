package gj.pf4j.socketio.cluster;

import java.util.Collection;
import java.util.Set;

public interface ITargetResolver {

    Set<String> resolveTargets(String hubName,
                               Collection<String> targetGroups,
                               Collection<String> targetUserIds);
}
