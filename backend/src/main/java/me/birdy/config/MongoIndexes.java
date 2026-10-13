package me.birdy.config;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/** Creates the MongoDB indexes the queries rely on; creation is a no-op when they already exist. */
@ApplicationScoped
public class MongoIndexes {

    public static final String POSTS = "posts";
    public static final String MESSAGES = "messages";

    private static final Logger LOG = System.getLogger(MongoIndexes.class.getName());

    @Inject
    private MongoDatabase database;

    void onStartup(@Observes Startup event) {
        // Feeds: posts of a set of authors, newest first.
        database.getCollection(POSTS).createIndex(
                Indexes.compoundIndex(Indexes.ascending("authorId"), Indexes.descending("_id")));
        // Account deletion: comments written by one user, across all posts.
        database.getCollection(POSTS).createIndex(Indexes.ascending("comments.authorId"));
        // Chat history: last messages of a room.
        database.getCollection(MESSAGES).createIndex(
                Indexes.compoundIndex(Indexes.ascending("room"), Indexes.descending("_id")));
        LOG.log(Level.INFO, "MongoDB indexes are in place");
    }
}
