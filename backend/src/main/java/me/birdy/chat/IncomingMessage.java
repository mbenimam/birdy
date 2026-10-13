package me.birdy.chat;

/** The frame a client sends over the WebSocket. */
public record IncomingMessage(String content) {

    static final int MAX_LENGTH = 1000;

    /** The trimmed content, or {@code null} when the frame does not carry 1 to 1000 characters of text. */
    String validContent() {
        if (content == null) {
            return null;
        }
        String trimmed = content.strip();
        return trimmed.isEmpty() || trimmed.length() > MAX_LENGTH ? null : trimmed;
    }
}
