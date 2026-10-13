package me.birdy.posts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import me.birdy.users.UserRef;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

class PostMapperTest {

    private static final ObjectId POST_ID = new ObjectId("65f0a1b2c3d4e5f601234567");
    private static final Instant CREATED_AT = Instant.parse("2026-03-04T05:06:07.890Z");
    private static final long AUTHOR = 11;
    private static final long OTHER = 22;

    private static Document comment(long authorId, String username, String content) {
        return new Document("_id", new ObjectId())
                .append("authorId", authorId)
                .append("authorUsername", username)
                .append("content", content)
                .append("createdAt", Date.from(CREATED_AT));
    }

    private static Document storedPost(List<Long> likes, List<Document> comments) {
        return new Document("_id", POST_ID)
                .append("authorId", AUTHOR)
                .append("authorUsername", "alice")
                .append("content", "hello")
                .append("createdAt", Date.from(CREATED_AT))
                .append("likes", likes)
                .append("comments", comments);
    }

    @Test
    void aStoredPostIsMappedWithCountersComputedFromItsArrays() {
        Document post = storedPost(List.of(OTHER, 33L), List.of(comment(OTHER, "bob", "nice")));

        PostView view = PostMapper.toPostView(post, OTHER);

        assertEquals(new PostView("65f0a1b2c3d4e5f601234567", new UserRef(AUTHOR, "alice"), "hello", CREATED_AT,
                2, true, 1, false), view);
    }

    @Test
    void ownershipAndLikesDependOnTheViewer() {
        Document post = storedPost(List.of(OTHER), List.of());

        PostView seenByAuthor = PostMapper.toPostView(post, AUTHOR);
        PostView seenByOther = PostMapper.toPostView(post, OTHER);

        assertTrue(seenByAuthor.ownedByMe());
        assertFalse(seenByAuthor.likedByMe());
        assertFalse(seenByOther.ownedByMe());
        assertTrue(seenByOther.likedByMe());
        assertEquals(1, seenByAuthor.likeCount());
        assertEquals(0, seenByAuthor.commentCount());
    }

    @Test
    void aProjectedPostIsMappedFromItsPrecomputedCounters() {
        Document projected = new Document("_id", POST_ID)
                .append("authorId", AUTHOR)
                .append("authorUsername", "alice")
                .append("content", "hello")
                .append("createdAt", Date.from(CREATED_AT))
                .append("likeCount", 5)
                .append("likedByViewer", true)
                .append("commentCount", 3);

        PostView view = PostMapper.toPostView(projected, OTHER);

        assertEquals(new PostView("65f0a1b2c3d4e5f601234567", new UserRef(AUTHOR, "alice"), "hello", CREATED_AT,
                5, true, 3, false), view);
    }

    @Test
    void aCommentIsMappedWithItsOwnership() {
        Document comment = comment(OTHER, "bob", "nice");
        String id = comment.getObjectId("_id").toHexString();

        assertEquals(new CommentView(id, new UserRef(OTHER, "bob"), "nice", CREATED_AT, true),
                PostMapper.toCommentView(comment, OTHER));
        assertEquals(new CommentView(id, new UserRef(OTHER, "bob"), "nice", CREATED_AT, false),
                PostMapper.toCommentView(comment, AUTHOR));
    }
}
