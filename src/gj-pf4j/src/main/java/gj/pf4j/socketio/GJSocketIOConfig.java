/*
 * Copyright (c) 2025 grejeff.
 */

package gj.pf4j.socketio;

import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.listener.ExceptionListener;
import com.corundumstudio.socketio.protocol.JacksonJsonSupport;
import gj.pf4j.utils.OSUtils;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.List;

@Configuration
public class GJSocketIOConfig {

    private final Environment env;

    private static final Logger log = LoggerFactory.getLogger(GJSocketIOConfig.class);

    public GJSocketIOConfig(Environment env) {
        this.env = env;
    }

    @Value("${socketio.port:9600}")
    private Integer port;

    @Value("${socketio.bossCount:1}")
    private int bossCount;

    @Value("${socketio.upgradeTimeout:10000}")
    private int upgradeTimeout;

    @Value("${socketio.pingTimeout:60000}")
    private int pingTimeout;

    @Value("${socketio.pingInterval:30000}")
    private int pingInterval;

    @Value("${socketio.maxConnections:50000}")
    private int maxConnections;

    public int getMaxConnections() {
        return maxConnections;
    }

    @Value("${socketio.maxFramePayloadLength:64}")
    private int maxFramePayloadLength;

    @Value("${socketio.maxHttpContentLength:64}")
    private int maxHttpContentLength;

    @Value("${socketio.maxConnectionsPerSecond:100}")
    private int maxConnectionsPerSecond;

    public int getMaxConnectionsPerSecond() {
        return maxConnectionsPerSecond;
    }

    @Value("${server.address:0.0.0.0}")
    private String serverAddress;

    @Value("${server.ssl.enabled:true}")
    private boolean sslEnabled;

    @Value("${server.ssl.enabled-protocols:TLSv1.2}")
    private String sslEnabledProtocols;

    @Bean
    public SocketIOServer socketIOServer() {
        final String serverAddress = this.serverAddress.isEmpty() ? "0.0.0.0" : this.serverAddress;
        SocketConfig socketConfig = new SocketConfig();
        // Disable Nagle's algorithm to send small packets immediately, reducing latency
        socketConfig.setTcpNoDelay(true);
        // Set SO_LINGER to 0, so connections are reset immediately (RST) on close, without waiting for unsent data
        socketConfig.setSoLinger(0);
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
        config.setSocketConfig(socketConfig);
        config.setHostname(serverAddress);
        config.setPort(port <= 0 ? 9600 : port);
        // Responsible for accepting new connections.
        config.setBossThreads(bossCount <= 0 ? 1 : bossCount);
        int workerThreads = 2 * Runtime.getRuntime().availableProcessors();
        // Worker thread count for handling I/O events (read/write).
        config.setWorkerThreads(workerThreads);
        // Whether to allow clients to send custom HTTP requests outside the Socket.IO protocol
        config.setAllowCustomRequests(false);
        config.setPingTimeout(pingTimeout);
        config.setPingInterval(pingInterval);
        // Maximum wait time for upgrading from HTTP polling to WebSocket (milliseconds)
        config.setUpgradeTimeout(upgradeTimeout);
        config.setJsonSupport(new JacksonJsonSupport());
        // Enable Deflate compression for WebSocket messages (RFC 7692), reducing bandwidth and improving transmission efficiency
        config.setWebsocketCompression(true);
        // Controls whether X-Powered-By: netty-socketio/x.x.x is added to HTTP response headers
        // Set to false to avoid exposing technology stack version, reducing attack surface
        config.setAddVersionHeader(false);
        // Enable CORS (Cross-Origin Resource Sharing) support
        //config.setEnableCors(true);
        // Set Access-Control-Allow-Headers value
        //config.setAllowHeaders("");
        config.setSSLProtocol(sslEnabledProtocols);
        // Whether to use Linux native epoll (via Netty's native transport)
        // NIO (Java standard, cross-platform)
        // Epoll (Linux only, requires netty-transport-native-epoll dependency, higher performance)
        config.setUseLinuxNativeEpoll(OSUtils.isLinux());
        // Max single WebSocket frame payload (bytes), prevents large payload DoS (OOM)
        config.setMaxFramePayloadLength(maxFramePayloadLength * 1024);
        // Max POST body size in HTTP polling mode, prevents large JSON DoS.
        config.setMaxHttpContentLength(maxHttpContentLength * 1024);
        // Explicitly set allowed origin domain or IP (for security)
        if (env.acceptsProfiles(Profiles.of("dev"))) {
            config.setOrigin(null);
        } else {
            if (sslEnabled) {
                config.setOrigin("https://" + serverAddress);
            } else {
                config.setOrigin("http://" + serverAddress);
            }
        }
        config.setExceptionListener(new ExceptionListener() {
            @Override
            public void onEventException(Exception e, List<Object> list, SocketIOClient socketIOClient) {
                log.error("Event error from onEventException: {}", e.getMessage());
            }

            @Override
            public void onDisconnectException(Exception e, SocketIOClient socketIOClient) {
                log.warn("Event error from onDisconnectException: {}", e.getMessage());
            }

            @Override
            public void onConnectException(Exception e, SocketIOClient socketIOClient) {
                log.warn("Event error from onConnectException: {}", e.getMessage());
            }

            @Override
            public void onPingException(Exception e, SocketIOClient socketIOClient) {
                log.warn("Event error from onPingException: {}", e.getMessage());
            }

            @Override
            public void onPongException(Exception e, SocketIOClient socketIOClient) {
                log.warn("Event error from onPongException: {}", e.getMessage());
            }

            @Override
            public boolean exceptionCaught(ChannelHandlerContext channelHandlerContext, Throwable throwable) throws Exception {
                return false;
            }

            @Override
            public void onAuthException(Throwable throwable, SocketIOClient socketIOClient) {

            }
        });
        return new SocketIOServer(config);
    }
}