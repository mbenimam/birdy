package me.birdy.chat;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Sorts;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import me.birdy.config.MongoIndexes;
import me.birdy.users.UserRef;
import org.bson.Document;
import org.bson.types.ObjectId;

/** MongoDB access for chat messages: {@code { _id, room, authorId, authorUsername, content, sentAt }}. */
@ApplicationScoped
public class MessageStore {

    private static final String ID = "_id";
    private static final String ROOM = "room";
    private static final String AUTHOR_ID = "authorId";
    private static final String AUTHOR_USERNAME = "authorUsername";
    private static final String CONTENT = "content";
    private static final String SENT_AT = "sentAt";

    @Inject
    private MongoDatabase database;

    private MongoCollection<Document> messages;

    @PostConstruct
    void init() {
        messages = database.getCollection(MongoIndexes.MESSAGES);
    }

    public ChatMessageView insert(String room, UserRef author, String content, Instant sentAt) {
        ObjectId id = new ObjectId();
        messages.insertOne(new Document(ID, id)
                .append(ROOM, room)
                .append(AUTHOR_ID, author.id())
                .append(AUTHOR_USERNAME, author.username())
                .append(CONTENT, content)
                .append(SENT_AT, Date.from(sentAt)));
        return ChatMessageView.of(id.toHexString(), room, author, content, sentAt);
    }

    /** The last {@code limit} messages of a room, oldest first. */
    public List<ChatMessageView> lastMessages(String room, int limit) {
        List<ChatMessageView> newestFirst = messages.find(eq(ROOM, room))
                .sort(Sorts.descending(ID))
                .limit(limit)
                .map(MessageStore::toView)
                .into(new ArrayList<>());
        return newestFirst.reversed();
    }

    private static ChatMessageView toView(Document message) {
        return ChatMessageView.of(
                message.getObjectId(ID).toHexString(),
                message.getString(ROOM),
                new UserRef(message.getLong(AUTHOR_ID), message.getString(AUTHOR_USERNAME)),
                message.getString(CONTENT),
                message.getDate(SENT_AT).toInstant());
    }
}
