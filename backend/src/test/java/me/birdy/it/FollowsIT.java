package me.birdy.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonObject;
import java.util.List;
import java.util.Set;
import me.birdy.it.Api.Account;
import org.junit.jupiter.api.Test;

class FollowsIT {

    @Test
    void followingIsIdempotent() {
        Account follower = Api.newAccount("follower");
        Account followed = Api.newAccount("followed");
        String path = "/api/users/me/following/" + followed.username();

        ApiResponse first = Api.put(path, follower.token());
        ApiResponse second = Api.put(path, follower.token());

        assertEquals(204, first.status());
        assertFalse(first.hasBody());
        assertEquals(204, second.status());
        JsonObject followedProfile = Api.get("/api/users/" + followed.username(), follower.token()).object();
        assertTrue(followedProfile.getBoolean("followedByMe"));
        assertEquals(1, followedProfile.getInt("followerCount"));
        assertEquals(0, followedProfile.getInt("followingCount"));
        assertEquals(1, Api.get("/api/users/me", follower.token()).object().getInt("followingCount"));
        assertEquals(1, Api.get("/api/users/" + follower.username() + "/following", follower.token()).array().size());
    }

    @Test
    void unfollowingIsIdempotent() {
        Account follower = Api.newAccount("follower");
        Account followed = Api.newAccount("followed");
        String path = "/api/users/me/following/" + followed.username();
        assertEquals(204, Api.put(path, follower.token()).status());

        ApiResponse first = Api.delete(path, follower.token());
        ApiResponse second = Api.delete(path, follower.token());

        assertEquals(204, first.status());
        assertEquals(204, second.status());
        JsonObject followedProfile = Api.get("/api/users/" + followed.username(), follower.token()).object();
        assertFalse(followedProfile.getBoolean("followedByMe"));
        assertEquals(0, followedProfile.getInt("followerCount"));
        assertEquals(0, Api.get("/api/users/me", follower.token()).object().getInt("followingCount"));
        assertEquals(204, Api.delete("/api/users/me/following/" + Api.uniqueName("ghost"), follower.token()).status(),
                "there is nothing to remove for an unknown user either");
    }

    @Test
    void followingAnUnknownUserIsNotFoundAndFollowingOneselfIsInvalid() {
        Account account = Api.newAccount("lonely");

        ApiResponse unknown = Api.put("/api/users/me/following/" + Api.uniqueName("ghost"), account.token());
        ApiResponse self = Api.put("/api/users/me/following/" + account.username(), account.token());

        assertEquals(404, unknown.status());
        assertEquals("not_found", unknown.error());
        assertEquals(400, self.status());
        assertEquals("validation_failed", self.error());
        assertEquals(Set.of("username"), self.fields().keySet());
        assertEquals(0, Api.get("/api/users/me", account.token()).object().getInt("followingCount"));
    }

    @Test
    void followingAndFollowersListsAreRelativeToTheViewer() {
        Account alice = Api.newAccount("alice");
        Account bob = Api.newAccount("bob");
        Account carol = Api.newAccount("carol");
        // alice follows bob and carol; bob follows carol.
        assertEquals(204, Api.put("/api/users/me/following/" + bob.username(), alice.token()).status());
        assertEquals(204, Api.put("/api/users/me/following/" + carol.username(), alice.token()).status());
        assertEquals(204, Api.put("/api/users/me/following/" + carol.username(), bob.token()).status());

        List<JsonObject> aliceFollowsSeenByBob =
                Api.get("/api/users/" + alice.username() + "/following", bob.token()).objects();
        List<JsonObject> carolFollowersSeenByBob =
                Api.get("/api/users/" + carol.username() + "/followers", bob.token()).objects();

        assertEquals(Set.of(bob.username(), carol.username()), usernames(aliceFollowsSeenByBob));
        assertFalse(summary(aliceFollowsSeenByBob, bob).getBoolean("followedByMe"), "bob does not follow himself");
        assertTrue(summary(aliceFollowsSeenByBob, carol).getBoolean("followedByMe"), "bob follows carol");
        assertEquals(carol.id(), summary(aliceFollowsSeenByBob, carol).getJsonNumber("id").longValue());

        assertEquals(Set.of(alice.username(), bob.username()), usernames(carolFollowersSeenByBob));
        assertFalse(summary(carolFollowersSeenByBob, alice).getBoolean("followedByMe"), "bob does not follow alice");

        assertEquals(0, Api.get("/api/users/" + carol.username() + "/following", bob.token()).array().size());
        assertEquals(404, Api.get("/api/users/" + Api.uniqueName("ghost") + "/following", bob.token()).status());
        assertEquals(404, Api.get("/api/users/" + Api.uniqueName("ghost") + "/followers", bob.token()).status());
    }

    private static Set<String> usernames(List<JsonObject> summaries) {
        return Set.copyOf(summaries.stream().map(user -> user.getString("username")).toList());
    }

    private static JsonObject summary(List<JsonObject> summaries, Account account) {
        return summaries.stream()
                .filter(user -> user.getString("username").equals(account.username()))
                .findFirst()
                .orElseThrow();
    }
}
