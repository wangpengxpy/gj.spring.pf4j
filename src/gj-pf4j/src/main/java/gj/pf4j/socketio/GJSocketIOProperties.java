package gj.pf4j.socketio;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("gj.socketio")
public class GJSocketIOProperties {

    private boolean enabled = false;
    private String host = "0.0.0.0";
    private int port = 9600;
    private int bossThreadCount = 1;
    private int upgradeTimeout = 10000;
    private int pingTimeout = 60000;
    private int pingInterval = 30000;
    private int maxConnections = 50000;
    private int maxFramePayloadLength = 64;
    private int maxHttpContentLength = 64;
    private int maxConnectionsPerSecond = 100;
    private String nodeId = "";
    private int connectionTtl = 3600;
    private final Ssl ssl = new Ssl();
    private final Cluster cluster = new Cluster();

    @Getter
    @Setter
    public static class Ssl {
        private boolean enabled = true;
        private String protocols = "TLSv1.2";
    }

    @Getter
    @Setter
    public static class Cluster {
        private boolean enabled = false;
    }
}
