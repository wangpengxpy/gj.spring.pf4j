package ${packagePrefix}.${pluginName}.hubs;

import gj.pf4j.socketio.GJHub;
import gj.pf4j.socketio.GJHubCallerContext;
import gj.pf4j.socketio.GJHubMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ${pluginName} Socket.IO Hub.
 *
 * <p>Clients connect via socket.io and route messages by hub name:
 * <pre>{@code
 * const socket = io("http://localhost:9600", {
 *     query: { hubName: "${pluginName}Hub" }
 * });
 * }</pre>
 */
@Slf4j
@Component
public class ${pluginName}Hub extends GJHub {

    public ${pluginName}Hub() {
        super("${pluginName}Hub");
    }

    @Override
    public CompletableFuture<Void> onConnectedAsync() {
        GJHubCallerContext ctx = getContext();
        log.info("Client connected: connectionId={}", ctx.getConnectionId());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> onDisconnectedAsync() {
        GJHubCallerContext ctx = getContext();
        log.info("Client disconnected: connectionId={}", ctx.getConnectionId());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Handle "sendMessage" event from the client.
     */
    @GJHubMethod("sendMessage")
    public void onSendMessage(Map<String, Object> data) {
        GJHubCallerContext ctx = getContext();
        log.info("Message : {}", data);

        // Broadcast to all connected clients except sender
        getClients().others().sendAsync("newMessage", data);
    }

    /**
     * Handle "joinGroup" event — add the caller to a group.
     */
    @GJHubMethod("joinGroup")
    public void onJoinGroup(String groupName) {
        getGroups().addToGroupAsync(groupName);
        log.info("{} joined group: {}", getContext().getUserId(), groupName);
    }

    /**
     * Handle "leaveGroup" event — remove the caller from a group.
     */
    @GJHubMethod("leaveGroup")
    public void onLeaveGroup(String groupName) {
        getGroups().removeFromGroupAsync(groupName);
        log.info("{} left group: {}", getContext().getUserId(), groupName);
    }
}
