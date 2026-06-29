package gj.plugin.demo.hubs;

import gj.pf4j.socketio.GJHub;
import gj.pf4j.socketio.GJHubMethod;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class DemoHub extends GJHub {

    public DemoHub() {
        super("demoHub");
    }

    @Override
    public CompletableFuture<Void> onConnectedAsync() {
        log.info("[DemoHub] Client connected: userId={}, connectionId={}",
                getContext().getUserId(), getContext().getConnectionId());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onDisconnectedAsync() {
        log.info("[DemoHub] Client disconnected: userId={}",
                getContext().getUserId());
        return CompletableFuture.completedFuture(null);
    }

    @GJHubMethod("echo")
    public void echo(String message) {
        log.info("[DemoHub] Received echo: {}", message);
        getClients().caller().sendAsync("echo_reply",
                Map.of("original", message, "timestamp", System.currentTimeMillis()));
    }

    @GJHubMethod("broadcast")
    public void broadcast(String message) {
        log.info("[DemoHub] Broadcasting: {}", message);
        getClients().others().sendAsync("broadcast",
                Map.of("message", message, "from", getContext().getUserId()));
    }
}
