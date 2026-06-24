package gj.pf4j.socketio.cluster;

import gj.pf4j.redis.IGJRedisService;
import gj.pf4j.socketio.GJSocketIOThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "socketio.cluster.enabled", havingValue = "true")
class ClusterCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(ClusterCleanupScheduler.class);

    private static final String CONN_KEY_PREFIX = "socketio:conn:";
    private static final String CONN_GROUPS_SUFFIX = ":groups";
    private static final String GROUP_KEY_PREFIX = "socketio:group:";
    private static final String USER_KEY_PREFIX = "socketio:user:";
    private static final String NODE_CONNS_PREFIX = "socketio:node:";
    private static final String NODE_CONNS_SUFFIX = ":connections";
    private static final String HEARTBEAT_KEY_PREFIX = "socketio:node:";
    private static final String HEARTBEAT_SUFFIX = ":heartbeat";
    private static final String CLEANUP_LOCK_PREFIX = "socketio:cleanup:";
    private static final String CLEANUP_LOCK_SUFFIX = ":lock";

    private static final int SCAN_INTERVAL_SECONDS = 30;
    private static final int CLEANUP_LOCK_TTL_SECONDS = 120;

    private final IGJRedisService redisService;
    private final String selfNodeId;
    private final ScheduledExecutorService scheduler;

    ClusterCleanupScheduler(IGJRedisService redisService,
                            @Value("${socketio.node-id:}") String nodeId) {
        this.redisService = redisService;
        this.selfNodeId = nodeId;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                new GJSocketIOThreadFactory.Builder()
                        .setNameFormat("cluster-cleanup-%d")
                        .setDaemon(true)
                        .build());
        scheduler.scheduleAtFixedRate(this::scanAndCleanup,
                SCAN_INTERVAL_SECONDS, SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("Cluster cleanup scheduler started (interval={}s)", SCAN_INTERVAL_SECONDS);
    }

    private void scanAndCleanup() {
        try {
            Set<String> heartbeatKeys = redisService.keys(
                    HEARTBEAT_KEY_PREFIX + "*" + HEARTBEAT_SUFFIX);
            for (String key : heartbeatKeys) {
                // Extract nodeId from key: socketio:node:nodeA:heartbeat → nodeA
                String nodeId = key.substring(
                        HEARTBEAT_KEY_PREFIX.length(),
                        key.length() - HEARTBEAT_SUFFIX.length());
                if (selfNodeId.equals(nodeId)) {
                    continue; // skip self
                }
                if (!redisService.exists(key)) {
                    // heartbeat already disappeared since we scanned
                    continue;
                }
            }
        } catch (Exception e) {
            log.warn("Heartbeat scan failed: {}", e.getMessage());
        }

        // Detect dead nodes: nodes we know about but whose heartbeat is gone
        try {
            Set<String> allNodeKeys = redisService.keys(
                    NODE_CONNS_PREFIX + "*" + NODE_CONNS_SUFFIX);
            for (String nodeKey : allNodeKeys) {
                String nodeId = nodeKey.substring(
                        NODE_CONNS_PREFIX.length(),
                        nodeKey.length() - NODE_CONNS_SUFFIX.length());
                if (selfNodeId.equals(nodeId)) {
                    continue;
                }
                String heartbeatKey = HEARTBEAT_KEY_PREFIX + nodeId + HEARTBEAT_SUFFIX;
                if (redisService.exists(heartbeatKey)) {
                    continue; // node is alive
                }

                // Try to acquire cleanup lock
                String lockKey = CLEANUP_LOCK_PREFIX + nodeId + CLEANUP_LOCK_SUFFIX;
                if (!redisService.setIfAbsent(lockKey, selfNodeId,
                        Duration.ofSeconds(CLEANUP_LOCK_TTL_SECONDS))) {
                    continue; // another node is cleaning up
                }

                log.warn("Node {} heartbeat lost, triggering cleanup", nodeId);
                try {
                    int cleanedCount = cleanupFaultedNode(nodeId);
                    log.info("Node {} fault cleanup completed: {} connections", nodeId, cleanedCount);
                } finally {
                    redisService.del(lockKey);
                    redisService.del(nodeKey);
                    redisService.del(heartbeatKey);
                }
            }
        } catch (Exception e) {
            log.warn("Dead node scan failed: {}", e.getMessage());
        }
    }

    private int cleanupFaultedNode(String nodeId) {
        int count = 0;
        Set<String> connectionIds = redisService.smembers(
                NODE_CONNS_PREFIX + nodeId + NODE_CONNS_SUFFIX);
        for (String cid : connectionIds) {
            try {
                // Clean up group memberships
                Set<String> groups = redisService.smembers(
                        CONN_KEY_PREFIX + cid + CONN_GROUPS_SUFFIX);
                for (String group : groups) {
                    redisService.srem(GROUP_KEY_PREFIX + group, cid);
                }
                // The userId would need to be reverse-looked-up; use a best-effort scan
                // In practice, conn and group keys are TTL'd as ultimate fallback
                redisService.del(CONN_KEY_PREFIX + cid);
                redisService.del(CONN_KEY_PREFIX + cid + CONN_GROUPS_SUFFIX);
                count++;
            } catch (Exception e) {
                log.warn("Failed to clean up connection {} for faulted node {}: {}",
                        cid, nodeId, e.getMessage());
            }
        }
        return count;
    }
}
