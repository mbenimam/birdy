package me.birdy.chat;

import java.time.Instant;
import me.birdy.users.UserRef;

/** A stored chat message, as returned by the history endpoint and pushed over the WebSocket. */
public record ChatMessageView(String type, String id, String room, UserRef author, String content, Instant sentAt) {

    static final String TYPE = "message";

    static ChatMessageView of(String id, String room, UserRef author, String content, Instant sentAt) {
        return new ChatMessageView(TYPE, id, room, author, content, sentAt);
    }
}
