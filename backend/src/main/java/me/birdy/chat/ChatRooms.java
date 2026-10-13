package me.birdy.chat;

import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.Session;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The connections currently open on each chat room, and the refusal of those that may not join one. Joining, leaving and broadcasting are serialised, so every
 * connection of a room sees presence frames and messages in the same order.
 */
@ApplicationScoped
public class ChatRooms {

    private static final Logger LOG = System.getLogger(ChatRooms.class.getName());
    private static final long REJECTION_DELAY_MILLIS = 100;

    /** Room name to its open connections with the username behind each. */
    private final Map<String, Map<Session, String>> rooms = new HashMap<>();

    @Inject
    private Jsonb jsonb;

    @Resource
    private ManagedScheduledExecutorService scheduler;

    /**
     * Closes a connection that presented no valid ticket with code 1008 (policy violation). The close is scheduled
     * shortly after the opening callback instead of being done inside it: the server is then still finishing the
     * HTTP upgrade of that connection, and tearing it down at that moment makes Payara log internal errors.
     */
    public void reject(Session session) {
        scheduler.schedule(() -> {
            try {
                session.close(new CloseReason(CloseCodes.VIOLATED_POLICY, "A valid chat ticket is required"));
            } catch (IOException e) {
                LOG.log(Level.DEBUG, "Could not close a rejected chat connection: {0}", e.toString());
            }
        }, REJECTION_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    public synchronized void join(String room, Session session, String username) {
        rooms.computeIfAbsent(room, name -> new LinkedHashMap<>()).put(session, username);
        sendPresence(room);
    }

    public synchronized void leave(String room, Session session) {
        Map<Session, String> members = rooms.get(room);
        if (members == null || members.remove(session) == null) {
            return;
        }
        if (members.isEmpty()) {
            rooms.remove(room);
        } else {
            sendPresence(room);
        }
    }

    public synchronized void broadcast(String room, ChatMessageView message) {
        send(room, jsonb.toJson(message));
    }

    private void sendPresence(String room) {
        List<String> usernames = rooms.get(room).values().stream().distinct().sorted().toList();
        send(room, jsonb.toJson(PresenceView.of(room, usernames)));
    }

    private void send(String room, String frame) {
        for (Session session : rooms.getOrDefault(room, Map.of()).keySet()) {
            if (session.isOpen()) {
                // Asynchronous, so one slow client cannot hold up the room.
                session.getAsyncRemote().sendText(frame, result -> {
                    if (!result.isOK()) {
                        LOG.log(Level.DEBUG, "Could not deliver a chat frame: {0}", result.getException());
                    }
                });
            }
        }
    }
}
