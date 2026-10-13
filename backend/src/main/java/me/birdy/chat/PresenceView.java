package me.birdy.chat;

import java.util.List;

/** The usernames currently connected to a room, pushed over the WebSocket on every join and leave. */
public record PresenceView(String type, String room, List<String> users) {

    static final String TYPE = "presence";

    static PresenceView of(String room, List<String> users) {
        return new PresenceView(TYPE, room, users);
    }
}
