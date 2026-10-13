package me.birdy.chat;

import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbException;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import me.birdy.users.UserRef;

/**
 * The chat WebSocket. A connection is accepted only with an unused, unexpired ticket from
 * {@code POST /api/chat/tickets}; otherwise it is closed with code 1008 (policy violation).
 */
@ServerEndpoint("/ws/chat/{room}")
public class ChatEndpoint {

    private static final Logger LOG = System.getLogger(ChatEndpoint.class.getName());
    private static final String TICKET_PARAMETER = "ticket";
    private static final String USER_PROPERTY = "birdy.user";
    /** Enough for 1000 characters of content with JSON escaping; larger frames are refused by the container. */
    private static final int MAX_FRAME_CHARS = 8 * 1024;

    @Inject
    private ChatTicketService tickets;

    @Inject
    private ChatRooms rooms;

    @Inject
    private MessageStore messages;

    @Inject
    private Jsonb jsonb;

    @Inject
    private Clock clock;

    @OnOpen
    public void onOpen(Session session, @PathParam("room") String room) {
        Optional<UserRef> user = RoomNames.isValid(room) ? tickets.redeem(ticketOf(session)) : Optional.empty();
        if (user.isEmpty()) {
            rooms.reject(session);
            return;
        }
        session.setMaxTextMessageBufferSize(MAX_FRAME_CHARS);
        session.getUserProperties().put(USER_PROPERTY, user.get());
        rooms.join(room, session, user.get().username());
    }

    @OnMessage
    public void onMessage(Session session, String frame, @PathParam("room") String room) {
        UserRef user = (UserRef) session.getUserProperties().get(USER_PROPERTY);
        String content = contentOf(frame);
        if (user == null || content == null) {
            // Frames from unauthenticated connections and frames that break the contract are dropped.
            return;
        }
        rooms.broadcast(room, messages.insert(room, user, content, clock.instant().truncatedTo(ChronoUnit.MILLIS)));
    }

    @OnClose
    public void onClose(Session session, @PathParam("room") String room) {
        rooms.leave(room, session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        LOG.log(Level.DEBUG, "Chat connection {0} failed: {1}", session.getId(), error);
    }

    private static String ticketOf(Session session) {
        List<String> values = session.getRequestParameterMap().get(TICKET_PARAMETER);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private String contentOf(String frame) {
        try {
            IncomingMessage message = jsonb.fromJson(frame, IncomingMessage.class);
            return message == null ? null : message.validContent();
        } catch (JsonbException e) {
            return null;
        }
    }
}
