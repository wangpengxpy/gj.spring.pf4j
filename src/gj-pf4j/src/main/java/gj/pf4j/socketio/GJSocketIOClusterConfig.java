package gj.pf4j.socketio;

import gj.pf4j.redis.IGJRedisBusService;
import gj.pf4j.redis.IGJRedisService;
import gj.pf4j.socketio.cluster.*;

import com.corundumstudio.socketio.SocketIOServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean(SocketIOServer.class)
public class GJSocketIOClusterConfig {

    private static final Logger log = LoggerFactory.getLogger(GJSocketIOClusterConfig.class);

    // ===== Monolith mode (default) =====

    @Bean
    @ConditionalOnMissingBean(IMessageRouter.class)
    public IMessageRouter defaultMessageRouter(GJHubManager hubManager) {
        log.info("Socket.IO cluster disabled — using DefaultMessageRouter");
        return new DefaultMessageRouter(hubManager.getClientRegistry());
    }

    @Bean
    @ConditionalOnMissingBean(ITargetResolver.class)
    public ITargetResolver defaultTargetResolver(GJHubManager hubManager) {
        return new DefaultTargetResolver(
                hubManager.getGroupConnections(),
                hubManager.getUserConnections(),
                hubManager.getClientRegistry());
    }

    @Bean
    @ConditionalOnMissingBean(IConnectionEventHandler.class)
    public IConnectionEventHandler defaultConnectionEventHandler() {
        return new NoopConnectionEventHandler();
    }

    // ===== Cluster mode =====

    @Bean
    @ConditionalOnProperty(name = "socketio.cluster.enabled", havingValue = "true")
    @ConditionalOnMissingBean(IGJRedisService.class)
    public Object clusterRequiresRedisGuard() {
        throw new IllegalStateException(
                "Socket.IO cluster mode requires Redis. " +
                "Please add the following dependency:\n" +
                "<dependency>\n" +
                "    <groupId>org.springframework.boot</groupId>\n" +
                "    <artifactId>spring-boot-starter-data-redis</artifactId>\n" +
                "</dependency>");
    }

    @Bean
    @ConditionalOnBean({IGJRedisService.class, IGJRedisBusService.class})
    @ConditionalOnProperty(name = "socketio.cluster.enabled", havingValue = "true")
    public IMessageRouter clusterMessageRouter(
            GJHubManager hubManager,
            IGJRedisService redisService,
            IGJRedisBusService busService,
            @Value("${socketio.node-id:}") String nodeId) {
        log.info("Socket.IO cluster enabled — using ClusterMessageRouter (node={})", resolveNodeId(nodeId));
        return new ClusterMessageRouter(
                hubManager.getClientRegistry(), redisService, busService,
                resolveNodeId(nodeId));
    }

    @Bean
    @ConditionalOnBean(IGJRedisService.class)
    @ConditionalOnProperty(name = "socketio.cluster.enabled", havingValue = "true")
    public ITargetResolver clusterTargetResolver(
            GJHubManager hubManager,
            IGJRedisService redisService) {
        return new ClusterTargetResolver(
                hubManager.getGroupConnections(),
                hubManager.getUserConnections(),
                hubManager.getClientRegistry(),
                redisService);
    }

    @Bean
    @ConditionalOnBean({IGJRedisService.class, IGJRedisBusService.class})
    @ConditionalOnProperty(name = "socketio.cluster.enabled", havingValue = "true")
    public IConnectionEventHandler clusterConnectionEventHandler(
            GJHubManager hubManager,
            IGJRedisService redisService,
            IGJRedisBusService busService,
            @Value("${socketio.node-id:}") String nodeId,
            @Value("${socketio.connection-ttl:3600}") int connectionTtl) {
        return new ClusterConnectionEventHandler(
                hubManager.getClientRegistry(), redisService, busService,
                resolveNodeId(nodeId), connectionTtl);
    }

    private static String resolveNodeId(String configuredNodeId) {
        if (configuredNodeId != null && !configuredNodeId.isBlank()) {
            return configuredNodeId;
        }
        String hostname = System.getenv("HOSTNAME");
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        try {
            return java.net.InetAddress.getLocalHost().getHostName()
                    + ":" + ProcessHandle.current().pid();
        } catch (Exception e) {
            return "node-" + ProcessHandle.current().pid();
        }
    }
}
