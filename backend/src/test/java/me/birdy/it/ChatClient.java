package me.birdy.it;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.StringReader;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** A WebSocket client for the chat endpoint that records the frames and the close code it receives. */
public final class ChatClient implements WebSocket.Listener, AutoCloseable {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final BlockingQueue<JsonObject> frames = new LinkedBlockingQueue<>();
    private final CompletableFuture<Integer> closeCode = new CompletableFuture<>();
    private final StringBuilder partial = new StringBuilder();
    private WebSocket socket;

    private ChatClient() {
    }

    /** Opens a connection to a room; {@code query} is appended verbatim to the URL (for example "?ticket=abc"). */
    public static ChatClient connect(String room, String query) {
        ChatClient client = new ChatClient();
        client.socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .connectTimeout(TIMEOUT)
                .buildAsync(BirdyStack.get().webSocketUri("/ws/chat/" + room + query), client)
                .join();
        return client;
    }

    public static ChatClient connectWithTicket(String room, String ticket) {
        return connect(room, "?ticket=" + ticket);
    }

    public void sendText(String text) {
        socket.sendText(text, true).join();
    }

    public void sendContent(String content) {
        sendText(Json.createObjectBuilder().add("content", content).build().toString());
    }

    /** Returns the next frame received, failing when none arrives in time. */
    public JsonObject nextFrame() throws InterruptedException {
        JsonObject frame = frames.poll(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (frame == null) {
            throw new AssertionError("No WebSocket frame received within " + TIMEOUT);
        }
        return frame;
    }

    /** Returns the next frame of the given type, skipping frames of other types. */
    public JsonObject nextFrameOfType(String type) throws InterruptedException {
        while (true) {
            JsonObject frame = nextFrame();
            if (type.equals(frame.getString("type", null))) {
                return frame;
            }
        }
    }

    /** Returns whether no frame arrives during the given time. */
    public boolean staysSilentFor(Duration duration) throws InterruptedException {
        return frames.poll(duration.toMillis(), TimeUnit.MILLISECONDS) == null;
    }

    /** Waits for the server to close the connection and returns the close code. */
    public int awaitCloseCode() throws Exception {
        return closeCode.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        partial.append(data);
        if (last) {
            try (JsonReader reader = Json.createReader(new StringReader(partial.toString()))) {
                frames.add(reader.readObject());
            }
            partial.setLength(0);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        closeCode.complete(statusCode);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        closeCode.completeExceptionally(error);
    }

    @Override
    public void close() {
        if (socket.isOutputClosed()) {
            return;
        }
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye").join();
        } catch (CompletionException e) {
            // The server closed the connection first; there is nothing left to close.
        }
    }
}
