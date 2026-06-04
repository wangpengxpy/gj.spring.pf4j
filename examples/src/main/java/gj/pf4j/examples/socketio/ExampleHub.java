package gj.pf4j.examples.hubs;

import gj.pf4j.socketio.GJHub;
import gj.pf4j.socketio.GJHubCallerContext;
import gj.pf4j.socketio.GJHubMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class ExampleHub extends GJHub {

    public ExampleHub() {
        super("exampleHub");
    }

    @Override
    public CompletableFuture<Void> onConnectedAsync() {
        GJHubCallerContext ctx = getContext();
        log.info("Client connected: userId={}", ctx.getUserId());
        // Notify others that a new user joined
        getClients().others().sendAsync("userJoined", Map.of("userId", ctx.getUserId()));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onDisconnectedAsync() {
        log.info("Client disconnected: userId={}", getContext().getUserId());
        return CompletableFuture.completedFuture(null);
    }

    @GJHubMethod("sendMessage")
    public void onSendMessage(Map<String, Object> data) {
        // Broadcast message to all clients except sender
        getClients().others().sendAsync("newMessage", data);
    }

    @GJHubMethod("joinGroup")
    public void onJoinGroup(String groupName) {
        getGroups().addToGroupAsync(groupName);
    }
}
