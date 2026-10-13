package me.birdy.it;

import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/** A small HTTP client for the Birdy API, used by the integration tests. */
public final class Api {

    public static final String PASSWORD = "correct horse battery";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private Api() {
    }

    /** A user that exists on the server, with a session token. */
    public record Account(long id, String username, String email, String token) {
    }

    public static ApiResponse get(String path, String token) {
        return send("GET", path, token, null);
    }

    public static ApiResponse post(String path, String token, JsonObject body) {
        return send("POST", path, token, body == null ? null : body.toString());
    }

    public static ApiResponse put(String path, String token) {
        return send("PUT", path, token, null);
    }

    public static ApiResponse delete(String path, String token) {
        return send("DELETE", path, token, null);
    }

    /** Sends a request; {@code token} and {@code body} are optional. */
    public static ApiResponse send(String method, String path, String token, String body) {
        HttpRequest.Builder request = HttpRequest.newBuilder(BirdyStack.get().httpUri(path))
                .timeout(Duration.ofSeconds(30))
                .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return send(request.build());
    }

    public static ApiResponse send(HttpRequest request) {
        try {
            HttpResponse<String> response = HTTP.send(request, BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new ApiResponse(response.statusCode(), parse(response.body()),
                    response.headers().firstValue("Content-Type").orElse(null));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Returns a username that no other test uses, starting with the given prefix. */
    public static String uniqueName(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static JsonObject signupBody(String username, String email) {
        return Json.createObjectBuilder()
                .add("username", username)
                .add("email", email)
                .add("password", PASSWORD)
                .add("birthday", "1995-03-21")
                .build();
    }

    public static ApiResponse login(String username, String password) {
        return post("/api/sessions", null,
                Json.createObjectBuilder().add("username", username).add("password", password).build());
    }

    /** Signs a new user up and logs it in. */
    public static Account newAccount(String prefix) {
        String username = uniqueName(prefix);
        String email = username + "@example.org";
        ApiResponse signup = post("/api/users", null, signupBody(username, email));
        if (signup.status() != 201) {
            throw new AssertionError("Signup failed: " + signup);
        }
        ApiResponse login = login(username, PASSWORD);
        if (login.status() != 201) {
            throw new AssertionError("Login failed: " + login);
        }
        return new Account(signup.object().getJsonNumber("id").longValue(), username, email,
                login.object().getString("token"));
    }

    /** Creates a post and returns its id. */
    public static String newPost(Account author, String content) {
        ApiResponse created = post("/api/posts", author.token(), content(content));
        if (created.status() != 201) {
            throw new AssertionError("Post creation failed: " + created);
        }
        return created.object().getString("id");
    }

    public static JsonObject content(String content) {
        return Json.createObjectBuilder().add("content", content).build();
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static JsonValue parse(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            return reader.readValue();
        } catch (JsonException e) {
            // Not JSON (for example an HTML error page of the server): keep the text so that tests can report it.
            return Json.createValue(body);
        }
    }
}
