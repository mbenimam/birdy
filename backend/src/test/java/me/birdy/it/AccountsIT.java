package me.birdy.it;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import me.birdy.it.Api.Account;
import org.junit.jupiter.api.Test;

class AccountsIT {

    @Test
    void signupCreatesTheUserAndReturnsItsOwnProfile() {
        String username = Api.uniqueName("signup");
        String email = username + "@example.org";

        ApiResponse response = Api.post("/api/users", null, Api.signupBody(username, email));

        assertEquals(201, response.status());
        JsonObject user = response.object();
        assertAll(
                () -> assertTrue(user.getJsonNumber("id").longValue() > 0),
                () -> assertEquals(username, user.getString("username")),
                () -> assertEquals(email, user.getString("email")),
                () -> assertEquals("1995-03-21", user.getString("birthday")),
                () -> assertEquals(0, user.getInt("followingCount")),
                () -> assertEquals(0, user.getInt("followerCount")),
                () -> assertTrue(Instant.parse(user.getString("createdAt")).isAfter(Instant.now().minusSeconds(300))),
                () -> assertEquals(
                        Set.of("id", "username", "email", "birthday", "createdAt", "followingCount", "followerCount"),
                        user.keySet(), "nothing else, in particular no password, may be exposed"));
    }

    @Test
    void signupWithInvalidValuesReportsEveryField() {
        JsonObject body = Json.createObjectBuilder()
                .add("username", "no spaces allowed")
                .add("email", "not-an-email")
                .add("password", "short")
                .add("birthday", "2999-01-01")
                .build();

        ApiResponse response = Api.post("/api/users", null, body);

        assertEquals(400, response.status());
        assertEquals("validation_failed", response.error());
        assertFalse(response.message().isBlank());
        assertEquals(Set.of("username", "email", "password", "birthday"), response.fields().keySet());
        assertEquals("must be a well-formed email address", response.fields().getString("email"));
    }

    @Test
    void signupEnforcesUsernameAndPasswordLengthLimits() {
        String tooLongName = "a".repeat(31);
        String tooLongPassword = "p".repeat(129);

        ApiResponse longName = Api.post("/api/users", null, Api.signupBody(tooLongName, "long@example.org"));
        ApiResponse shortName = Api.post("/api/users", null, Api.signupBody("ab", "short@example.org"));
        ApiResponse longPassword = Api.post("/api/users", null, Json.createObjectBuilder(
                Api.signupBody(Api.uniqueName("pw"), "pw@example.org")).add("password", tooLongPassword).build());

        assertEquals(400, longName.status());
        assertEquals(Set.of("username"), longName.fields().keySet());
        assertEquals(400, shortName.status());
        assertEquals(Set.of("username"), shortName.fields().keySet());
        assertEquals(400, longPassword.status());
        assertEquals(Set.of("password"), longPassword.fields().keySet());
    }

    @Test
    void signupWithMissingPropertiesOrMalformedJsonIsAValidationFailure() {
        ApiResponse empty = Api.post("/api/users", null, Json.createObjectBuilder().build());
        ApiResponse malformed = Api.send("POST", "/api/users", null, "{\"username\": ");
        ApiResponse noBody = Api.send(HttpRequest.newBuilder(BirdyStack.get().httpUri("/api/users"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build());

        assertEquals(400, empty.status());
        assertEquals(Set.of("username", "email", "password", "birthday"), empty.fields().keySet());
        assertEquals(400, malformed.status());
        assertEquals("validation_failed", malformed.error());
        assertEquals(400, noBody.status());
        assertEquals("validation_failed", noBody.error());
    }

    @Test
    void signupRejectsATakenUsernameOrEmailWithConflict() {
        Account existing = Api.newAccount("taken");

        ApiResponse sameUsername = Api.post("/api/users", null,
                Api.signupBody(existing.username(), "other-" + existing.email()));
        ApiResponse sameEmail = Api.post("/api/users", null,
                Api.signupBody(Api.uniqueName("fresh"), existing.email()));

        assertEquals(409, sameUsername.status());
        assertEquals("conflict", sameUsername.error());
        assertEquals("The username or email is already taken", sameUsername.message(),
                "the application answers itself, before the unique index has to refuse the row");
        assertFalse(sameUsername.object().containsKey("fields"));
        assertEquals(409, sameEmail.status());
        assertEquals("conflict", sameEmail.error());
    }

    @Test
    void signupRejectsTheReservedUsernameMe() {
        ApiResponse response = Api.post("/api/users", null, Api.signupBody("me", "me@example.org"));

        assertEquals(400, response.status());
        assertEquals(Set.of("username"), response.fields().keySet());
    }

    @Test
    void loginReturnsATokenThatAuthenticatesTheUser() {
        Account account = Api.newAccount("login");

        ApiResponse login = Api.login(account.username(), Api.PASSWORD);

        assertEquals(201, login.status());
        JsonObject session = login.object();
        String token = session.getString("token");
        Instant expiresAt = Instant.parse(session.getString("expiresAt"));
        assertEquals(43, token.length(), "32 random bytes in unpadded URL-safe Base64");
        assertNotEquals(account.token(), token, "every login gets its own token");
        // The stack runs with BIRDY_SESSION_TTL_HOURS=1.
        assertTrue(expiresAt.isAfter(Instant.now().plus(Duration.ofMinutes(55))));
        assertTrue(expiresAt.isBefore(Instant.now().plus(Duration.ofMinutes(65))));
        assertEquals(account.username(), session.getJsonObject("user").getString("username"));
        assertEquals(account.email(), session.getJsonObject("user").getString("email"));

        ApiResponse me = Api.get("/api/users/me", token);
        assertEquals(200, me.status());
        assertEquals(account.id(), me.object().getJsonNumber("id").longValue());
        assertEquals(account.email(), me.object().getString("email"));
        assertEquals("1995-03-21", me.object().getString("birthday"));
    }

    @Test
    void loginFailsTheSameWayForAWrongPasswordAndAnUnknownUser() {
        Account account = Api.newAccount("badlogin");

        ApiResponse wrongPassword = Api.login(account.username(), "not the password");
        ApiResponse unknownUser = Api.login(Api.uniqueName("ghost"), Api.PASSWORD);

        assertEquals(401, wrongPassword.status());
        assertEquals(401, unknownUser.status());
        assertEquals("unauthorized", wrongPassword.error());
        assertEquals(wrongPassword.body(), unknownUser.body(), "the answers must be indistinguishable");
    }

    @Test
    void protectedRoutesRejectMissingAndGarbageTokens() {
        ApiResponse missing = Api.get("/api/users/me", null);
        ApiResponse garbage = Api.get("/api/users/me", "garbage");
        ApiResponse wellFormedButUnknown = Api.get("/api/users/me", "A".repeat(43));
        ApiResponse wrongScheme = Api.send(HttpRequest.newBuilder(BirdyStack.get().httpUri("/api/users/me"))
                .header("Authorization", "Basic dXNlcjpwYXNz")
                .build());

        for (ApiResponse response : List.of(missing, garbage, wellFormedButUnknown, wrongScheme)) {
            assertEquals(401, response.status());
            assertEquals("unauthorized", response.error());
            assertFalse(response.message().isBlank());
        }
        assertEquals(401, Api.get("/api/posts", null).status());
        assertEquals(401, Api.post("/api/posts", null, Api.content("anonymous")).status());
        assertEquals(401, Api.post("/api/chat/tickets", null, null).status());
        assertEquals(401, Api.get("/api/chat/rooms/lobby/messages", null).status());
        assertEquals(401, Api.delete("/api/sessions/current", null).status());
    }

    @Test
    void anExpiredSessionIsRejectedAndRemoved() throws Exception {
        Account account = Api.newAccount("expired");
        assertEquals(200, Api.get("/api/users/me", account.token()).status());

        expireSession(account.token());

        ApiResponse response = Api.get("/api/users/me", account.token());
        assertEquals(401, response.status());
        assertEquals("unauthorized", response.error());
        assertEquals(0, countSessions(account.token()), "presenting an expired token deletes its session");
    }

    @Test
    void expiredSessionsArePurgedInTheBackground() throws Exception {
        Account account = Api.newAccount("purged");
        String otherToken = Api.login(account.username(), Api.PASSWORD).object().getString("token");
        expireSession(account.token());

        Instant deadline = Instant.now().plusSeconds(10L * BirdyStack.SESSION_CLEANUP_SECONDS);
        while (countSessions(account.token()) > 0 && Instant.now().isBefore(deadline)) {
            Thread.sleep(200);
        }

        assertEquals(0, countSessions(account.token()), "the expired session was never presented, yet it is gone");
        assertEquals(1, countSessions(otherToken), "sessions that are still valid are kept");
        assertEquals(200, Api.get("/api/users/me", otherToken).status());
    }

    @Test
    void logoutDeletesOnlyTheSessionOfThePresentedToken() {
        Account account = Api.newAccount("logout");
        String secondToken = Api.login(account.username(), Api.PASSWORD).object().getString("token");

        ApiResponse logout = Api.delete("/api/sessions/current", account.token());

        assertEquals(204, logout.status());
        assertFalse(logout.hasBody());
        assertEquals(401, Api.get("/api/users/me", account.token()).status());
        assertEquals(401, Api.delete("/api/sessions/current", account.token()).status());
        assertEquals(200, Api.get("/api/users/me", secondToken).status());
    }

    @Test
    void onlyDigestsAndSaltedHashesAreStored() throws Exception {
        Account account = Api.newAccount("storage");

        try (Connection connection = BirdyStack.get().jdbc()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT password_hash FROM users WHERE id = ?")) {
                statement.setLong(1, account.id());
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    String[] hash = rows.getString(1).split("\\$");
                    assertEquals(4, hash.length);
                    assertEquals("pbkdf2-sha256", hash[0]);
                    assertTrue(Integer.parseInt(hash[1]) >= 600_000);
                    assertFalse(rows.getString(1).contains(Api.PASSWORD));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT token_digest FROM user_sessions WHERE user_id = ?")) {
                statement.setLong(1, account.id());
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(sha256Hex(account.token()), rows.getString(1));
                    assertFalse(rows.next());
                }
            }
        }
    }

    @Test
    void theProfileOfAnotherUserHidesPrivateProperties() {
        Account viewer = Api.newAccount("viewer");
        Account other = Api.newAccount("other");

        ApiResponse profile = Api.get("/api/users/" + other.username(), viewer.token());

        assertEquals(200, profile.status());
        assertEquals(
                Set.of("id", "username", "createdAt", "followingCount", "followerCount", "followedByMe"),
                profile.object().keySet());
        assertEquals(other.id(), profile.object().getJsonNumber("id").longValue());
        assertFalse(profile.object().getBoolean("followedByMe"));

        ApiResponse unknown = Api.get("/api/users/" + Api.uniqueName("ghost"), viewer.token());
        assertEquals(404, unknown.status());
        assertEquals("not_found", unknown.error());
    }

    @Test
    void searchMatchesSubstringsCaseInsensitivelyAndNeverReturnsTheCaller() {
        String marker = Api.uniqueName("S");
        Account caller = Api.newAccount(marker + "a");
        Account second = Api.newAccount(marker + "b");
        Account third = Api.newAccount(marker + "c");
        assertEquals(204, Api.put("/api/users/me/following/" + third.username(), caller.token()).status());

        ApiResponse found = Api.get("/api/users?q=" + marker.toUpperCase(), caller.token());

        assertEquals(200, found.status());
        assertEquals(List.of(second.username(), third.username()),
                found.objects().stream().map(user -> user.getString("username")).sorted().toList());
        JsonObject thirdSummary = found.objects().stream()
                .filter(user -> user.getString("username").equals(third.username())).findFirst().orElseThrow();
        assertEquals(Set.of("id", "username", "followedByMe"), thirdSummary.keySet());
        assertTrue(thirdSummary.getBoolean("followedByMe"));

        assertEquals(1, Api.get("/api/users?q=" + marker + "&limit=1", caller.token()).array().size());
        assertEquals(0, Api.get("/api/users?q=" + Api.uniqueName("nobody"), caller.token()).array().size());
    }

    @Test
    void searchTreatsWildcardsLiterallyAndValidatesItsParameters() {
        Account caller = Api.newAccount("wild");
        Api.newAccount("wild");

        ApiResponse percent = Api.get("/api/users?q=" + Api.encode("%"), caller.token());
        ApiResponse everyone = Api.get("/api/users?limit=1000", caller.token());
        ApiResponse badLimit = Api.get("/api/users?limit=abc", caller.token());
        ApiResponse zeroLimit = Api.get("/api/users?limit=0", caller.token());

        assertEquals(200, percent.status());
        assertEquals(0, percent.array().size(), "no username contains a percent sign");
        assertEquals(200, everyone.status());
        assertFalse(everyone.array().isEmpty());
        assertTrue(everyone.array().size() <= 50, "the limit is capped at 50");
        assertTrue(everyone.objects().stream().noneMatch(user -> user.getString("username").equals(caller.username())));
        assertEquals(400, badLimit.status());
        assertEquals(Set.of("limit"), badLimit.fields().keySet());
        assertEquals(400, zeroLimit.status());
    }

    @Test
    void deletingAnAccountRemovesEverythingItOwns() throws Exception {
        Account leaving = Api.newAccount("leaving");
        Account staying = Api.newAccount("staying");
        String postOfLeaving = Api.newPost(leaving, "I will disappear");
        String postOfStaying = Api.newPost(staying, "I will stay");
        assertEquals(201, Api.post("/api/posts/" + postOfStaying + "/comments", leaving.token(),
                Api.content("comment that will disappear")).status());
        assertEquals(201, Api.post("/api/posts/" + postOfStaying + "/comments", staying.token(),
                Api.content("comment that will stay")).status());
        assertEquals(201, Api.post("/api/posts/" + postOfLeaving + "/comments", staying.token(),
                Api.content("comment on a post that will disappear")).status());
        assertEquals(204, Api.put("/api/posts/" + postOfStaying + "/like", leaving.token()).status());
        assertEquals(204, Api.put("/api/users/me/following/" + staying.username(), leaving.token()).status());
        assertEquals(204, Api.put("/api/users/me/following/" + leaving.username(), staying.token()).status());

        ApiResponse deletion = Api.delete("/api/users/me", leaving.token());

        assertEquals(204, deletion.status());
        assertEquals(401, Api.get("/api/users/me", leaving.token()).status(), "its sessions are gone");
        assertEquals(401, Api.login(leaving.username(), Api.PASSWORD).status(), "it can no longer log in");
        assertEquals(404, Api.get("/api/users/" + leaving.username(), staying.token()).status());
        assertEquals(404, Api.get("/api/posts/" + postOfLeaving, staying.token()).status(), "its posts are gone");

        JsonObject remainingPost = Api.get("/api/posts/" + postOfStaying, staying.token()).object();
        assertEquals(1, remainingPost.getInt("commentCount"), "its comments are gone, others are kept");
        assertEquals(0, remainingPost.getInt("likeCount"), "its likes are gone");
        List<JsonObject> comments = Api.get("/api/posts/" + postOfStaying + "/comments", staying.token()).objects();
        assertEquals(List.of("comment that will stay"),
                comments.stream().map(comment -> comment.getString("content")).toList());

        JsonObject stayingProfile = Api.get("/api/users/me", staying.token()).object();
        assertEquals(0, stayingProfile.getInt("followingCount"), "its follow links are gone, in both directions");
        assertEquals(0, stayingProfile.getInt("followerCount"));
        assertEquals(0, Api.get("/api/posts?feed=home", staying.token()).objects().stream()
                .filter(post -> post.getJsonObject("author").getString("username").equals(leaving.username()))
                .count());
        assertEquals(0, countRows("SELECT COUNT(*) FROM user_sessions WHERE user_id = ?", leaving.id()));
        assertEquals(0, countRows(
                "SELECT COUNT(*) FROM follows WHERE follower_id = ? OR followed_id = ?", leaving.id(), leaving.id()));

        assertEquals(201, Api.post("/api/users", null, Api.signupBody(leaving.username(), leaving.email())).status(),
                "the username and email are free again");
    }

    @Test
    void unknownRoutesAndMethodsAnswerWithTheErrorBody() {
        Account account = Api.newAccount("routes");

        ApiResponse unknownRoute = Api.get("/api/does-not-exist", account.token());
        ApiResponse wrongMethod = Api.send("PATCH", "/api/users/me", account.token(), "{}");

        assertEquals(404, unknownRoute.status());
        assertEquals("not_found", unknownRoute.error());
        assertTrue(unknownRoute.contentType().startsWith("application/json"));
        assertEquals(405, wrongMethod.status());
        assertFalse(wrongMethod.message().isBlank());
    }

    private static void expireSession(String token) throws Exception {
        try (Connection connection = BirdyStack.get().jdbc();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE user_sessions SET expires_at = UTC_TIMESTAMP(3) - INTERVAL 1 SECOND"
                                + " WHERE token_digest = ?")) {
            statement.setString(1, sha256Hex(token));
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static long countSessions(String token) throws Exception {
        return countRows("SELECT COUNT(*) FROM user_sessions WHERE token_digest = ?", sha256Hex(token));
    }

    private static long countRows(String sql, Object... parameters) throws Exception {
        try (Connection connection = BirdyStack.get().jdbc();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static String sha256Hex(String text) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
