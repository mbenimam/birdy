package me.birdy.posts;

import java.time.Instant;
import java.util.List;
import me.birdy.users.UserRef;
import org.bson.Document;

/** Converts between MongoDB documents and the API views of posts and comments. */
final class PostMapper {

    static final String ID = "_id";
    static final String AUTHOR_ID = "authorId";
    static final String AUTHOR_USERNAME = "authorUsername";
    static final String CONTENT = "content";
    static final String CREATED_AT = "createdAt";
    static final String LIKES = "likes";
    static final String COMMENTS = "comments";
    static final String LIKE_COUNT = "likeCount";
    static final String LIKED_BY_VIEWER = "likedByViewer";
    static final String COMMENT_COUNT = "commentCount";

    private PostMapper() {
    }

    /**
     * Maps a post document. It works on stored documents (with the {@code likes} and {@code comments} arrays) and on
     * the projection made by {@link PostStore} (with the precomputed counters instead).
     */
    static PostView toPostView(Document post, long viewerId) {
        List<Long> likes = post.getList(LIKES, Long.class);
        List<Document> comments = post.getList(COMMENTS, Document.class);
        long authorId = post.getLong(AUTHOR_ID);
        return new PostView(
                post.getObjectId(ID).toHexString(),
                new UserRef(authorId, post.getString(AUTHOR_USERNAME)),
                post.getString(CONTENT),
                instant(post),
                likes != null ? likes.size() : number(post, LIKE_COUNT),
                likes != null ? likes.contains(viewerId) : post.getBoolean(LIKED_BY_VIEWER, false),
                comments != null ? comments.size() : number(post, COMMENT_COUNT),
                authorId == viewerId);
    }

    static CommentView toCommentView(Document comment, long viewerId) {
        long authorId = comment.getLong(AUTHOR_ID);
        return new CommentView(
                comment.getObjectId(ID).toHexString(),
                new UserRef(authorId, comment.getString(AUTHOR_USERNAME)),
                comment.getString(CONTENT),
                instant(comment),
                authorId == viewerId);
    }

    private static Instant instant(Document document) {
        return document.getDate(CREATED_AT).toInstant();
    }

    private static long number(Document document, String key) {
        Number value = document.get(key, Number.class);
        return value == null ? 0 : value.longValue();
    }
}
