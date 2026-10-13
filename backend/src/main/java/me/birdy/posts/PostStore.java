package me.birdy.posts;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.lt;
import static me.birdy.posts.PostMapper.AUTHOR_ID;
import static me.birdy.posts.PostMapper.AUTHOR_USERNAME;
import static me.birdy.posts.PostMapper.COMMENTS;
import static me.birdy.posts.PostMapper.COMMENT_COUNT;
import static me.birdy.posts.PostMapper.CONTENT;
import static me.birdy.posts.PostMapper.CREATED_AT;
import static me.birdy.posts.PostMapper.ID;
import static me.birdy.posts.PostMapper.LIKED_BY_VIEWER;
import static me.birdy.posts.PostMapper.LIKES;
import static me.birdy.posts.PostMapper.LIKE_COUNT;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import me.birdy.config.MongoIndexes;
import me.birdy.users.UserRef;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

/**
 * MongoDB access for posts. A post document embeds its comments and the ids of the users who like it:
 *
 * <pre>
 * { _id, authorId, authorUsername, content, createdAt, likes: [userId],
 *   comments: [ { _id, authorId, authorUsername, content, createdAt } ] }
 * </pre>
 *
 * ObjectIds grow with time, so "newest first" and the {@code before} cursor are both expressed on {@code _id}.
 */
@ApplicationScoped
public class PostStore {

    @Inject
    private MongoDatabase database;

    private MongoCollection<Document> posts;

    @PostConstruct
    void init() {
        posts = database.getCollection(MongoIndexes.POSTS);
    }

    public PostView insert(UserRef author, String content, Instant createdAt) {
        Document post = new Document(ID, new ObjectId())
                .append(AUTHOR_ID, author.id())
                .append(AUTHOR_USERNAME, author.username())
                .append(CONTENT, content)
                .append(CREATED_AT, Date.from(createdAt))
                .append(LIKES, List.of())
                .append(COMMENTS, List.of());
        posts.insertOne(post);
        return PostMapper.toPostView(post, author.id());
    }

    public Optional<PostView> find(ObjectId id, long viewerId) {
        return list(eq(ID, id), 1, viewerId).stream().findFirst();
    }

    /** Posts of the given authors older than {@code before} (when present), newest first. */
    public List<PostView> listByAuthors(Collection<Long> authorIds, Optional<ObjectId> before, int limit,
            long viewerId) {
        Bson authors = in(AUTHOR_ID, authorIds);
        Bson filter = before.map(cursor -> and(authors, lt(ID, cursor))).orElse(authors);
        return list(filter, limit, viewerId);
    }

    public Optional<Long> authorOf(ObjectId id) {
        Document post = posts.find(eq(ID, id)).projection(Projections.include(AUTHOR_ID)).first();
        return Optional.ofNullable(post).map(found -> found.getLong(AUTHOR_ID));
    }

    public void delete(ObjectId id) {
        posts.deleteOne(eq(ID, id));
    }

    /** Adds the user to the likes of the post; returns {@code false} when the post does not exist. */
    public boolean like(ObjectId id, long userId) {
        return posts.updateOne(eq(ID, id), Updates.addToSet(LIKES, userId)).getMatchedCount() > 0;
    }

    /** Removes the user from the likes of the post; returns {@code false} when the post does not exist. */
    public boolean unlike(ObjectId id, long userId) {
        return posts.updateOne(eq(ID, id), Updates.pull(LIKES, userId)).getMatchedCount() > 0;
    }

    /** The comments of a post, oldest first, or empty when the post does not exist. */
    public Optional<List<CommentView>> comments(ObjectId postId, long viewerId) {
        Document post = posts.find(eq(ID, postId)).projection(Projections.include(COMMENTS)).first();
        return Optional.ofNullable(post).map(found -> found.getList(COMMENTS, Document.class).stream()
                .map(comment -> PostMapper.toCommentView(comment, viewerId))
                .toList());
    }

    /** Appends a comment; returns empty when the post does not exist. */
    public Optional<CommentView> addComment(ObjectId postId, UserRef author, String content, Instant createdAt) {
        Document comment = new Document(ID, new ObjectId())
                .append(AUTHOR_ID, author.id())
                .append(AUTHOR_USERNAME, author.username())
                .append(CONTENT, content)
                .append(CREATED_AT, Date.from(createdAt));
        boolean added = posts.updateOne(eq(ID, postId), Updates.push(COMMENTS, comment)).getMatchedCount() > 0;
        return added ? Optional.of(PostMapper.toCommentView(comment, author.id())) : Optional.empty();
    }

    /** The author of one comment of a post, or empty when the post or the comment does not exist. */
    public Optional<Long> commentAuthor(ObjectId postId, ObjectId commentId) {
        Document post = posts.find(and(eq(ID, postId), eq(COMMENTS + "." + ID, commentId)))
                .projection(Projections.elemMatch(COMMENTS, eq(ID, commentId)))
                .first();
        return Optional.ofNullable(post)
                .map(found -> found.getList(COMMENTS, Document.class).getFirst().getLong(AUTHOR_ID));
    }

    public void deleteComment(ObjectId postId, ObjectId commentId) {
        posts.updateOne(eq(ID, postId), Updates.pull(COMMENTS, eq(ID, commentId)));
    }

    /** Removes the posts of a user and the comments and likes they left on other posts. */
    public void deleteEverythingOf(long userId) {
        posts.deleteMany(eq(AUTHOR_ID, userId));
        posts.updateMany(eq(COMMENTS + "." + AUTHOR_ID, userId), Updates.pull(COMMENTS, eq(AUTHOR_ID, userId)));
        posts.updateMany(eq(LIKES, userId), Updates.pull(LIKES, userId));
    }

    /** Runs the listing pipeline; counters are computed by the server so the embedded arrays are not fetched. */
    private List<PostView> list(Bson filter, int limit, long viewerId) {
        Document projection = new Document(AUTHOR_ID, 1)
                .append(AUTHOR_USERNAME, 1)
                .append(CONTENT, 1)
                .append(CREATED_AT, 1)
                .append(LIKE_COUNT, new Document("$size", "$" + LIKES))
                .append(LIKED_BY_VIEWER, new Document("$in", List.of(viewerId, "$" + LIKES)))
                .append(COMMENT_COUNT, new Document("$size", "$" + COMMENTS));
        List<Bson> pipeline = List.of(
                Aggregates.match(filter),
                Aggregates.sort(Sorts.descending(ID)),
                Aggregates.limit(limit),
                Aggregates.project(projection));
        return posts.aggregate(pipeline)
                .map(post -> PostMapper.toPostView(post, viewerId))
                .into(new ArrayList<>());
    }
}
