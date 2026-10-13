package me.birdy.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.io.StringReader;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import me.birdy.posts.CreateCommentRequest;
import me.birdy.posts.CreatePostRequest;
import me.birdy.users.SignupRequest;
import me.birdy.users.UserView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** Checks that the records travel through JSON-B (the same implementation family the server uses) as the contract says. */
class JsonMappingTest {

    private static final Jsonb JSONB = JsonbBuilder.create();

    @AfterAll
    static void close() throws Exception {
        JSONB.close();
    }

    @Test
    void aSignupRequestIsReadFromJson() {
        SignupRequest request = JSONB.fromJson(
                "{\"username\":\"alice\",\"email\":\"a@example.org\",\"password\":\"secret123\","
                        + "\"birthday\":\"1990-05-17\",\"unknown\":true}",
                SignupRequest.class);

        assertEquals(new SignupRequest("alice", "a@example.org", "secret123", LocalDate.of(1990, 5, 17)), request);
    }

    @Test
    void missingPropertiesAreNullSoThatValidationCanReportThem() {
        SignupRequest request = JSONB.fromJson("{}", SignupRequest.class);

        assertNull(request.username());
        assertNull(request.birthday());
        assertEquals(Set.of("username", "email", "password", "birthday"), Validation.invalidFields(request));
    }

    @Test
    void contentIsTrimmedWhileItIsRead() {
        assertEquals("hello", JSONB.fromJson("{\"content\":\"  hello \\n\"}", CreatePostRequest.class).content());
        assertEquals("hi", JSONB.fromJson("{\"content\":\"\\thi \"}", CreateCommentRequest.class).content());
        assertNull(JSONB.fromJson("{}", CreatePostRequest.class).content());
    }

    @Test
    void anErrorWithoutFieldsHasNoFieldsProperty() {
        assertEquals(Set.of("error", "message"), json(new ApiError("not_found", "Post not found", null)).keySet());
    }

    @Test
    void aValidationErrorListsItsFields() {
        JsonObject error = json(new ApiError("validation_failed", "Request validation failed",
                Map.of("email", "must be a well-formed email address")));

        assertEquals("validation_failed", error.getString("error"));
        assertEquals("must be a well-formed email address", error.getJsonObject("fields").getString("email"));
    }

    @Test
    void unsetUserPropertiesAreLeftOutAndTimestampsAreIsoUtc() {
        UserView other = new UserView(7, "bob", null, null, Instant.parse("2026-02-03T04:05:06.789Z"), 1, 2, true);

        JsonObject user = json(other);

        assertEquals(Set.of("id", "username", "createdAt", "followingCount", "followerCount", "followedByMe"),
                user.keySet());
        assertEquals("2026-02-03T04:05:06.789Z", user.getString("createdAt"));
        assertEquals(7, user.getInt("id"));
    }

    @Test
    void anOwnProfileCarriesEmailAndBirthdayAsIsoDate() {
        UserView own = new UserView(7, "bob", "bob@example.org", LocalDate.of(1991, 12, 31),
                Instant.parse("2026-02-03T04:05:06Z"), 0, 0, null);

        JsonObject user = json(own);

        assertEquals("bob@example.org", user.getString("email"));
        assertEquals("1991-12-31", user.getString("birthday"));
        assertEquals(false, user.containsKey("followedByMe"));
    }

    private static JsonObject json(Object value) {
        return Json.createReader(new StringReader(JSONB.toJson(value))).readObject();
    }
}
