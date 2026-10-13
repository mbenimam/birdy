package me.birdy.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import me.birdy.it.Api.Account;
import org.junit.jupiter.api.Test;

class ChatIT {

    private static final int POLICY_VIOLATION = 1008;

    @Test
    void aTicketIsValidForThirtySeconds() {
        Account account = Api.newAccount("chatter");

        ApiResponse response = Api.post("/api/chat/tickets", account.token(), null);

        assertEquals(201, response.status());
        assertEquals(Set.of("ticket", "expiresAt"), response.object().keySet());
        assertFalse(response.object().getString("ticket").isBlank());
        Instant expiresAt = Instant.parse(response.object().getString("expiresAt"));
        assertTrue(expiresAt.isAfter(Instant.now().plusSeconds(20)));
        assertTrue(expiresAt.isBefore(Instant.now().plusSeconds(40)));
    }

    @Test
    void aTicketCanBeUsedOnlyOnce() throws Exception {
        Account account = Api.newAccount("chatter");
        String room = room();
        String ticket = ticket(account);

        try (ChatClient first = ChatClient.connectWithTicket(room, ticket)) {
            assertEquals(List.of(account.username()), users(first.nextFrameOfType("presence")));

            try (ChatClient second = ChatClient.connectWithTicket(room, ticket)) {
                assertEquals(POLICY_VIOLATION, second.awaitCloseCode());
            }
            // The first connection is unaffected and nobody else joined the room.
            assertTrue(first.staysSilentFor(Duration.ofMillis(500)));
        }
    }

    @Test
    void connectionsWithoutAValidTicketAreClosedWithPolicyViolation() throws Exception {
        String room = room();

        try (ChatClient missing = ChatClient.connect(room, "");
                ChatClient empty = ChatClient.connect(room, "?ticket=");
                ChatClient garbage = ChatClient.connectWithTicket(room, "garbage");
                ChatClient unknown = ChatClient.connectWithTicket(room, "A".repeat(43))) {
            assertEquals(POLICY_VIOLATION, missing.awaitCloseCode());
            assertEquals(POLICY_VIOLATION, empty.awaitCloseCode());
            assertEquals(POLICY_VIOLATION, garbage.awaitCloseCode());
            assertEquals(POLICY_VIOLATION, unknown.awaitCloseCode());
        }
    }

    @Test
    void aSessionTokenIsNotAChatTicket() throws Exception {
        Account account = Api.newAccount("chatter");

        try (ChatClient client = ChatClient.connectWithTicket(room(), account.token())) {
            assertEquals(POLICY_VIOLATION, client.awaitCloseCode());
        }
    }

    @Test
    void anInvalidRoomNameIsRefusedEvenWithAValidTicket() throws Exception {
        Account account = Api.newAccount("chatter");

        try (ChatClient client = ChatClient.connectWithTicket("Not_A_Valid_Room", ticket(account))) {
            assertEquals(POLICY_VIOLATION, client.awaitCloseCode());
        }
        ApiResponse history = Api.get("/api/chat/rooms/Not_A_Valid_Room/messages", account.token());
        assertEquals(400, history.status());
        assertEquals(Set.of("room"), history.fields().keySet());
    }

    @Test
    void everyConnectionOfARoomReceivesMessagesAndPresence() throws Exception {
        Account alice = Api.newAccount("a");
        Account bob = Api.newAccount("b");
        String room = room();

        try (ChatClient aliceClient = ChatClient.connectWithTicket(room, ticket(alice))) {
            JsonObject aliceAlone = aliceClient.nextFrame();
            assertEquals("presence", aliceAlone.getString("type"));
            assertEquals(room, aliceAlone.getString("room"));
            assertEquals(List.of(alice.username()), users(aliceAlone));

            try (ChatClient bobClient = ChatClient.connectWithTicket(room, ticket(bob))) {
                List<String> both = List.of(alice.username(), bob.username());
                assertEquals(both, users(aliceClient.nextFrame()), "alice is told that bob joined");
                assertEquals(both, users(bobClient.nextFrame()), "bob receives the presence of the room");

                aliceClient.sendContent("  hello bob  ");

                JsonObject receivedByAlice = aliceClient.nextFrame();
                JsonObject receivedByBob = bobClient.nextFrame();
                assertEquals(receivedByAlice, receivedByBob, "the sender gets the same frame as everybody else");
                assertEquals(Set.of("type", "id", "room", "author", "content", "sentAt"), receivedByBob.keySet());
                assertEquals("message", receivedByBob.getString("type"));
                assertTrue(receivedByBob.getString("id").matches("[0-9a-f]{24}"));
                assertEquals(room, receivedByBob.getString("room"));
                assertEquals("hello bob", receivedByBob.getString("content"));
                assertEquals(alice.username(), receivedByBob.getJsonObject("author").getString("username"));
                assertEquals(alice.id(), receivedByBob.getJsonObject("author").getJsonNumber("id").longValue());
                assertTrue(Instant.parse(receivedByBob.getString("sentAt")).isAfter(Instant.now().minusSeconds(300)));

                bobClient.sendContent("hello alice");
                assertEquals("hello alice", aliceClient.nextFrame().getString("content"));
                assertEquals(bob.username(), bobClient.nextFrame().getJsonObject("author").getString("username"));
            }

            JsonObject afterBobLeft = aliceClient.nextFrame();
            assertEquals("presence", afterBobLeft.getString("type"));
            assertEquals(List.of(alice.username()), users(afterBobLeft), "alice is told that bob left");
        }
    }

    @Test
    void messagesStayInsideTheirRoom() throws Exception {
        Account alice = Api.newAccount("a");
        Account bob = Api.newAccount("b");

        try (ChatClient aliceClient = ChatClient.connectWithTicket(room(), ticket(alice));
                ChatClient bobClient = ChatClient.connectWithTicket(room(), ticket(bob))) {
            assertEquals(List.of(alice.username()), users(aliceClient.nextFrame()));
            assertEquals(List.of(bob.username()), users(bobClient.nextFrame()));

            aliceClient.sendContent("only for my room");

            assertEquals("only for my room", aliceClient.nextFrame().getString("content"));
            assertTrue(bobClient.staysSilentFor(Duration.ofMillis(700)));
        }
    }

    @Test
    void invalidFramesAreIgnoredAndNotStored() throws Exception {
        Account account = Api.newAccount("chatter");
        String room = room();

        try (ChatClient client = ChatClient.connectWithTicket(room, ticket(account))) {
            client.nextFrameOfType("presence");

            client.sendText("this is not JSON");
            client.sendText("{}");
            client.sendContent("   ");
            client.sendContent("x".repeat(1001));
            client.sendContent("x".repeat(1000));

            assertEquals(1000, client.nextFrame().getString("content").length(),
                    "only the valid frame is broadcast");
        }
        assertEquals(1, Api.get("/api/chat/rooms/" + room + "/messages", account.token()).array().size());
    }

    @Test
    void historyReturnsTheLastMessagesOldestFirst() throws Exception {
        Account account = Api.newAccount("chatter");
        Account reader = Api.newAccount("reader");
        String room = room();
        try (ChatClient client = ChatClient.connectWithTicket(room, ticket(account))) {
            client.nextFrameOfType("presence");
            for (int i = 1; i <= 4; i++) {
                client.sendContent("message " + i);
                client.nextFrameOfType("message");
            }
        }

        ApiResponse all = Api.get("/api/chat/rooms/" + room + "/messages", reader.token());
        ApiResponse lastTwo = Api.get("/api/chat/rooms/" + room + "/messages?limit=2", reader.token());

        assertEquals(200, all.status());
        assertEquals(List.of("message 1", "message 2", "message 3", "message 4"), contents(all));
        assertEquals(List.of("message 3", "message 4"), contents(lastTwo));
        JsonObject stored = all.objects().get(0);
        assertEquals(Set.of("type", "id", "room", "author", "content", "sentAt"), stored.keySet());
        assertEquals("message", stored.getString("type"));
        assertEquals(room, stored.getString("room"));
        assertEquals(account.username(), stored.getJsonObject("author").getString("username"));

        assertEquals(0, Api.get("/api/chat/rooms/" + room() + "/messages", reader.token()).array().size());
        assertEquals(400, Api.get("/api/chat/rooms/" + room + "/messages?limit=zero", reader.token()).status());
    }

    private static String ticket(Account account) {
        ApiResponse response = Api.post("/api/chat/tickets", account.token(), null);
        assertEquals(201, response.status());
        return response.object().getString("ticket");
    }

    /** Returns a room name no other test uses. */
    private static String room() {
        return Api.uniqueName("room").replace('_', '-');
    }

    private static List<String> users(JsonObject presence) {
        return presence.getJsonArray("users").getValuesAs(JsonString.class).stream().map(JsonString::getString).toList();
    }

    private static List<String> contents(ApiResponse response) {
        return response.objects().stream().map(message -> message.getString("content")).toList();
    }
}
