package me.birdy.posts;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import me.birdy.api.ApiException;
import me.birdy.auth.AuthenticatedUser;
import me.birdy.users.FollowService;
import me.birdy.users.UserRef;
import me.birdy.users.UserService;
import org.bson.types.ObjectId;

/** Rules around posts, likes and comments. */
@ApplicationScoped
public class PostService {

    @Inject
    private PostStore store;

    @Inject
    private FollowService follows;

    @Inject
    private UserService users;

    @Inject
    private Clock clock;

    public PostView create(AuthenticatedUser author, String content) {
        return store.insert(ref(author), content, now());
    }

    /** The newest posts of the caller and of the users the caller follows. */
    public List<PostView> homeFeed(long viewerId, String before, int limit) {
        return store.listByAuthors(follows.homeFeedAuthors(viewerId), cursor(before), limit, viewerId);
    }

    /** The newest posts of one user; {@code 404} when the user does not exist. */
    public List<PostView> userFeed(String authorUsername, long viewerId, String before, int limit) {
        long authorId = users.idOf(authorUsername);
        return store.listByAuthors(List.of(authorId), cursor(before), limit, viewerId);
    }

    public PostView get(String id, long viewerId) {
        return store.find(postId(id), viewerId).orElseThrow(PostService::unknownPost);
    }

    /** Deletes a post; only its author may do so. */
    public void delete(String id, long callerId) {
        ObjectId postId = postId(id);
        long authorId = store.authorOf(postId).orElseThrow(PostService::unknownPost);
        if (authorId != callerId) {
            throw ApiException.forbidden("Only the author can delete a post");
        }
        store.delete(postId);
    }

    public void like(String id, long userId) {
        if (!store.like(postId(id), userId)) {
            throw unknownPost();
        }
    }

    public void unlike(String id, long userId) {
        if (!store.unlike(postId(id), userId)) {
            throw unknownPost();
        }
    }

    public List<CommentView> comments(String id, long viewerId) {
        return store.comments(postId(id), viewerId).orElseThrow(PostService::unknownPost);
    }

    public CommentView addComment(String id, AuthenticatedUser author, String content) {
        return store.addComment(postId(id), ref(author), content, now()).orElseThrow(PostService::unknownPost);
    }

    /** Deletes a comment; allowed for the author of the comment and for the author of the post. */
    public void deleteComment(String id, String commentId, long callerId) {
        ObjectId postId = postId(id);
        ObjectId parsedCommentId = ObjectIds.parse(commentId).orElseThrow(PostService::unknownComment);
        long postAuthorId = store.authorOf(postId).orElseThrow(PostService::unknownPost);
        long commentAuthorId = store.commentAuthor(postId, parsedCommentId).orElseThrow(PostService::unknownComment);
        if (callerId != commentAuthorId && callerId != postAuthorId) {
            throw ApiException.forbidden("Only the author of the comment or of the post can delete a comment");
        }
        store.deleteComment(postId, parsedCommentId);
    }

    private static ObjectId postId(String id) {
        return ObjectIds.parse(id).orElseThrow(PostService::unknownPost);
    }

    private static Optional<ObjectId> cursor(String before) {
        if (before == null || before.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(ObjectIds.parse(before.strip())
                .orElseThrow(() -> ApiException.invalid("before", "must be a post id")));
    }

    private static UserRef ref(AuthenticatedUser user) {
        return new UserRef(user.id(), user.username());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static ApiException unknownPost() {
        return ApiException.notFound("Post not found");
    }

    private static ApiException unknownComment() {
        return ApiException.notFound("Comment not found");
    }
}
