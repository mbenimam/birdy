package me.birdy.it;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonObject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import me.birdy.it.Api.Account;
import org.junit.jupiter.api.Test;

class PostsIT {

    private static final String UNKNOWN_ID = "0123456789abcdef01234567";

    @Test
    void creatingAPostTrimsItsContentAndReturnsThePost() {
        Account author = Api.newAccount("author");

        ApiResponse created = Api.post("/api/posts", author.token(), Api.content("  Hello Birdy \n"));

        assertEquals(201, created.status());
        JsonObject post = created.object();
        assertAll(
                () -> assertEquals(Set.of("id", "author", "content", "createdAt", "likeCount", "likedByMe",
                        "commentCount", "ownedByMe"), post.keySet()),
                () -> assertTrue(post.getString("id").matches("[0-9a-f]{24}")),
                () -> assertEquals("Hello Birdy", post.getString("content")),
                () -> assertEquals(author.id(), post.getJsonObject("author").getJsonNumber("id").longValue()),
                () -> assertEquals(author.username(), post.getJsonObject("author").getString("username")),
                () -> assertTrue(Instant.parse(post.getString("createdAt")).isAfter(Instant.now().minusSeconds(300))),
                () -> assertEquals(0, post.getInt("likeCount")),
                () -> assertFalse(post.getBoolean("likedByMe")),
                () -> assertEquals(0, post.getInt("commentCount")),
                () -> assertTrue(post.getBoolean("ownedByMe")));

        ApiResponse fetched = Api.get("/api/posts/" + post.getString("id"), author.token());
        assertEquals(200, fetched.status());
        assertEquals(post, fetched.object());
    }

    @Test
    void postContentMustHaveOneToFiveHundredCharactersAfterTrimming() {
        Account author = Api.newAccount("author");

        ApiResponse blank = Api.post("/api/posts", author.token(), Api.content("   "));
        ApiResponse missing = Api.send("POST", "/api/posts", author.token(), "{}");
        ApiResponse tooLong = Api.post("/api/posts", author.token(), Api.content("x".repeat(501)));
        ApiResponse longest = Api.post("/api/posts", author.token(), Api.content(" " + "x".repeat(500) + " "));

        for (ApiResponse invalid : List.of(blank, missing, tooLong)) {
            assertEquals(400, invalid.status());
            assertEquals("validation_failed", invalid.error());
            assertEquals(Set.of("content"), invalid.fields().keySet());
        }
        assertEquals(201, longest.status());
        assertEquals(500, longest.object().getString("content").length());
    }

    @Test
    void unknownAndMalformedPostIdsAreNotFound() {
        Account account = Api.newAccount("reader");

        for (String id : List.of(UNKNOWN_ID, "not-an-object-id", "123")) {
            for (ApiResponse response : List.of(
                    Api.get("/api/posts/" + id, account.token()),
                    Api.delete("/api/posts/" + id, account.token()),
                    Api.put("/api/posts/" + id + "/like", account.token()),
                    Api.delete("/api/posts/" + id + "/like", account.token()),
                    Api.get("/api/posts/" + id + "/comments", account.token()),
                    Api.post("/api/posts/" + id + "/comments", account.token(), Api.content("hello")))) {
                assertEquals(404, response.status(), "id " + id);
                assertEquals("not_found", response.error());
            }
        }
    }

    @Test
    void theHomeFeedContainsOnlyTheCallerAndTheUsersTheyFollow() {
        Account viewer = Api.newAccount("viewer");
        Account followed = Api.newAccount("followed");
        Account stranger = Api.newAccount("stranger");
        assertEquals(204, Api.put("/api/users/me/following/" + followed.username(), viewer.token()).status());
        // The stranger follows the viewer: that direction must not bring the stranger's posts into the feed.
        assertEquals(204, Api.put("/api/users/me/following/" + viewer.username(), stranger.token()).status());
        String own = Api.newPost(viewer, "mine");
        String fromFollowed = Api.newPost(followed, "from someone I follow");
        String fromStranger = Api.newPost(stranger, "from a stranger");
        String ownLater = Api.newPost(viewer, "mine again");

        ApiResponse feed = Api.get("/api/posts?feed=home", viewer.token());
        ApiResponse defaultFeed = Api.get("/api/posts", viewer.token());

        assertEquals(200, feed.status());
        assertEquals(List.of(ownLater, fromFollowed, own), ids(feed), "newest first, without the stranger's post");
        assertFalse(ids(feed).contains(fromStranger));
        assertEquals(ids(feed), ids(defaultFeed), "home is the default feed");
        assertTrue(feed.objects().get(0).getBoolean("ownedByMe"));
        assertFalse(feed.objects().get(1).getBoolean("ownedByMe"));

        assertEquals(204, Api.delete("/api/users/me/following/" + followed.username(), viewer.token()).status());
        assertEquals(List.of(ownLater, own), ids(Api.get("/api/posts?feed=home", viewer.token())),
                "unfollowing removes the posts from the feed");
    }

    @Test
    void theUserFeedContainsThePostsOfOneUser() {
        Account viewer = Api.newAccount("viewer");
        Account author = Api.newAccount("author");
        Api.newPost(viewer, "not in the author's feed");
        String first = Api.newPost(author, "first");
        String second = Api.newPost(author, "second");

        ApiResponse feed = Api.get("/api/posts?feed=user&author=" + author.username(), viewer.token());

        assertEquals(200, feed.status());
        assertEquals(List.of(second, first), ids(feed));
        assertEquals(404, Api.get("/api/posts?feed=user&author=" + Api.uniqueName("ghost"), viewer.token()).status());

        ApiResponse missingAuthor = Api.get("/api/posts?feed=user", viewer.token());
        assertEquals(400, missingAuthor.status());
        assertEquals(Set.of("author"), missingAuthor.fields().keySet());
        ApiResponse unknownFeed = Api.get("/api/posts?feed=trending", viewer.token());
        assertEquals(400, unknownFeed.status());
        assertEquals(Set.of("feed"), unknownFeed.fields().keySet());
    }

    @Test
    void beforePagesBackwardsThroughTheFeed() {
        Account author = Api.newAccount("pager");
        List<String> newestFirst = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            newestFirst.addFirst(Api.newPost(author, "post " + i));
        }

        List<String> firstPage = ids(Api.get("/api/posts?feed=home&limit=2", author.token()));
        List<String> secondPage = ids(Api.get(
                "/api/posts?feed=home&limit=2&before=" + firstPage.getLast(), author.token()));
        List<String> thirdPage = ids(Api.get(
                "/api/posts?feed=home&limit=2&before=" + secondPage.getLast(), author.token()));
        List<String> fourthPage = ids(Api.get(
                "/api/posts?feed=home&limit=2&before=" + thirdPage.getLast(), author.token()));

        assertEquals(newestFirst.subList(0, 2), firstPage);
        assertEquals(newestFirst.subList(2, 4), secondPage);
        assertEquals(newestFirst.subList(4, 5), thirdPage);
        assertEquals(List.of(), fourthPage);
        assertEquals(newestFirst.subList(1, 5), ids(Api.get(
                "/api/posts?feed=user&author=" + author.username() + "&before=" + newestFirst.getFirst(),
                author.token())), "before also pages the user feed");

        ApiResponse malformedCursor = Api.get("/api/posts?before=nonsense", author.token());
        assertEquals(400, malformedCursor.status());
        assertEquals(Set.of("before"), malformedCursor.fields().keySet());
    }

    @Test
    void theFeedReturnsTwentyPostsByDefaultAndAtMostFifty() {
        Account author = Api.newAccount("prolific");
        for (int i = 0; i < 52; i++) {
            Api.newPost(author, "post " + i);
        }

        assertEquals(20, Api.get("/api/posts", author.token()).array().size());
        assertEquals(50, Api.get("/api/posts?limit=50", author.token()).array().size());
        assertEquals(50, Api.get("/api/posts?limit=500", author.token()).array().size());
        assertEquals(400, Api.get("/api/posts?limit=-1", author.token()).status());
    }

    @Test
    void onlyTheAuthorCanDeleteAPost() {
        Account author = Api.newAccount("author");
        Account other = Api.newAccount("other");
        String postId = Api.newPost(author, "mine to delete");

        ApiResponse byOther = Api.delete("/api/posts/" + postId, other.token());

        assertEquals(403, byOther.status());
        assertEquals("forbidden", byOther.error());
        assertEquals(200, Api.get("/api/posts/" + postId, author.token()).status(), "the post is still there");

        ApiResponse byAuthor = Api.delete("/api/posts/" + postId, author.token());

        assertEquals(204, byAuthor.status());
        assertEquals(404, Api.get("/api/posts/" + postId, author.token()).status());
        assertEquals(404, Api.delete("/api/posts/" + postId, author.token()).status());
    }

    @Test
    void likingIsIdempotentAndCountedPerUser() {
        Account author = Api.newAccount("author");
        Account fan = Api.newAccount("fan");
        String postId = Api.newPost(author, "like me");
        String likePath = "/api/posts/" + postId + "/like";

        assertEquals(204, Api.put(likePath, fan.token()).status());
        assertEquals(204, Api.put(likePath, fan.token()).status());

        JsonObject seenByFan = Api.get("/api/posts/" + postId, fan.token()).object();
        JsonObject seenByAuthor = Api.get("/api/posts/" + postId, author.token()).object();
        assertEquals(1, seenByFan.getInt("likeCount"), "liking twice counts once");
        assertTrue(seenByFan.getBoolean("likedByMe"));
        assertEquals(1, seenByAuthor.getInt("likeCount"));
        assertFalse(seenByAuthor.getBoolean("likedByMe"));

        assertEquals(204, Api.put(likePath, author.token()).status());
        JsonObject inFeed = Api.get("/api/posts?feed=user&author=" + author.username(), fan.token()).objects().get(0);
        assertEquals(2, inFeed.getInt("likeCount"));
        assertTrue(inFeed.getBoolean("likedByMe"));

        assertEquals(204, Api.delete(likePath, fan.token()).status());
        assertEquals(204, Api.delete(likePath, fan.token()).status());
        JsonObject afterUnlike = Api.get("/api/posts/" + postId, fan.token()).object();
        assertEquals(1, afterUnlike.getInt("likeCount"), "unliking twice removes one like");
        assertFalse(afterUnlike.getBoolean("likedByMe"));
    }

    @Test
    void commentsAreAddedAndListedOldestFirst() {
        Account author = Api.newAccount("author");
        Account commenter = Api.newAccount("commenter");
        String postId = Api.newPost(author, "comment me");
        String commentsPath = "/api/posts/" + postId + "/comments";

        ApiResponse first = Api.post(commentsPath, commenter.token(), Api.content("  first!  "));
        ApiResponse second = Api.post(commentsPath, author.token(), Api.content("second"));

        assertEquals(201, first.status());
        JsonObject created = first.object();
        assertEquals(Set.of("id", "author", "content", "createdAt", "ownedByMe"), created.keySet());
        assertTrue(created.getString("id").matches("[0-9a-f]{24}"));
        assertEquals("first!", created.getString("content"));
        assertEquals(commenter.username(), created.getJsonObject("author").getString("username"));
        assertEquals(commenter.id(), created.getJsonObject("author").getJsonNumber("id").longValue());
        assertTrue(created.getBoolean("ownedByMe"));
        assertEquals(201, second.status());

        ApiResponse listed = Api.get(commentsPath, commenter.token());
        assertEquals(200, listed.status());
        assertEquals(List.of("first!", "second"),
                listed.objects().stream().map(comment -> comment.getString("content")).toList());
        assertEquals(created, listed.objects().get(0));
        assertFalse(listed.objects().get(1).getBoolean("ownedByMe"));
        assertEquals(2, Api.get("/api/posts/" + postId, author.token()).object().getInt("commentCount"));
    }

    @Test
    void commentContentMustHaveOneToThreeHundredCharacters() {
        Account author = Api.newAccount("author");
        String commentsPath = "/api/posts/" + Api.newPost(author, "comment me") + "/comments";

        ApiResponse blank = Api.post(commentsPath, author.token(), Api.content(" "));
        ApiResponse tooLong = Api.post(commentsPath, author.token(), Api.content("x".repeat(301)));
        ApiResponse longest = Api.post(commentsPath, author.token(), Api.content("x".repeat(300)));

        assertEquals(400, blank.status());
        assertEquals(Set.of("content"), blank.fields().keySet());
        assertEquals(400, tooLong.status());
        assertEquals(Set.of("content"), tooLong.fields().keySet());
        assertEquals(201, longest.status());
        assertEquals(1, Api.get(commentsPath, author.token()).array().size());
    }

    @Test
    void aCommentCanBeDeletedByItsAuthorOrByThePostAuthorOnly() {
        Account postAuthor = Api.newAccount("author");
        Account commenter = Api.newAccount("commenter");
        Account stranger = Api.newAccount("stranger");
        String postId = Api.newPost(postAuthor, "comment me");
        String commentsPath = "/api/posts/" + postId + "/comments";
        String firstComment = Api.post(commentsPath, commenter.token(), Api.content("one")).object().getString("id");
        String secondComment = Api.post(commentsPath, commenter.token(), Api.content("two")).object().getString("id");
        String thirdComment = Api.post(commentsPath, commenter.token(), Api.content("three")).object().getString("id");

        ApiResponse byStranger = Api.delete(commentsPath + "/" + firstComment, stranger.token());

        assertEquals(403, byStranger.status());
        assertEquals("forbidden", byStranger.error());
        assertEquals(3, Api.get(commentsPath, stranger.token()).array().size(), "nothing was deleted");

        assertEquals(204, Api.delete(commentsPath + "/" + firstComment, commenter.token()).status());
        assertEquals(204, Api.delete(commentsPath + "/" + secondComment, postAuthor.token()).status());

        List<JsonObject> remaining = Api.get(commentsPath, stranger.token()).objects();
        assertEquals(List.of(thirdComment), remaining.stream().map(comment -> comment.getString("id")).toList());
        assertEquals(1, Api.get("/api/posts/" + postId, stranger.token()).object().getInt("commentCount"));

        assertEquals(404, Api.delete(commentsPath + "/" + firstComment, commenter.token()).status());
        assertEquals(404, Api.delete(commentsPath + "/" + UNKNOWN_ID, commenter.token()).status());
        assertEquals(404, Api.delete(commentsPath + "/not-an-id", commenter.token()).status());
        assertEquals(404, Api.delete("/api/posts/" + UNKNOWN_ID + "/comments/" + thirdComment, commenter.token())
                .status());
    }

    private static List<String> ids(ApiResponse response) {
        return response.objects().stream().map(post -> post.getString("id")).toList();
    }
}
