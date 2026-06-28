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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.List;

@Configuration
@ConditionalOnProperty(name = "gj.socketio.enabled", havingValue = "true")
public class GJSocketIOConfig {

    private final Environment env;
    private final GJSocketIOProperties props;

    private static final Logger log = LoggerFactory.getLogger(GJSocketIOConfig.class);

    public GJSocketIOConfig(Environment env, GJSocketIOProperties props) {
        this.env = env;
        this.props = props;
    }

    @Bean
    public SocketIOServer socketIOServer() {
        final String host = props.getHost().isEmpty() ? "0.0.0.0" : props.getHost();
        SocketConfig socketConfig = new SocketConfig();
        // Disable Nagle's algorithm to send small packets immediately, reducing latency
        socketConfig.setTcpNoDelay(true);
        // Set SO_LINGER to 0, so connections are reset immediately (RST) on close, without waiting for unsent data
        socketConfig.setSoLinger(0);
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
        config.setSocketConfig(socketConfig);
        config.setHostname(host);
        config.setPort(props.getPort() <= 0 ? 9600 : props.getPort());
        // Responsible for accepting new connections.
        config.setBossThreads(props.getBossThreadCount() <= 0 ? 1 : props.getBossThreadCount());
        int workerThreads = 2 * Runtime.getRuntime().availableProcessors();
        // Worker thread count for handling I/O events (read/write).
        config.setWorkerThreads(workerThreads);
        // Whether to allow clients to send custom HTTP requests outside the Socket.IO protocol
        config.setAllowCustomRequests(false);
        config.setPingTimeout(props.getPingTimeout());
        config.setPingInterval(props.getPingInterval());
        // Maximum wait time for upgrading from HTTP polling to WebSocket (milliseconds)
        config.setUpgradeTimeout(props.getUpgradeTimeout());
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
        config.setSSLProtocol(props.getSsl().getProtocols());
        // Whether to use Linux native epoll (via Netty's native transport)
        // NIO (Java standard, cross-platform)
        // Epoll (Linux only, requires netty-transport-native-epoll dependency, higher performance)
        config.setUseLinuxNativeEpoll(OSUtils.isLinux());
        // Max single WebSocket frame payload (bytes), prevents large payload DoS (OOM)
        config.setMaxFramePayloadLength(props.getMaxFramePayloadLength() * 1024);
        // Max POST body size in HTTP polling mode, prevents large JSON DoS.
        config.setMaxHttpContentLength(props.getMaxHttpContentLength() * 1024);
        // Explicitly set allowed origin domain or IP (for security)
        if (env.acceptsProfiles(Profiles.of("dev"))) {
            config.setOrigin(null);
        } else {
            if (props.getSsl().isEnabled()) {
                config.setOrigin("https://" + host);
            } else {
                config.setOrigin("http://" + host);
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
