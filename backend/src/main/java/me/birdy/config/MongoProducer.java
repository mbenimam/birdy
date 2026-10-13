package me.birdy.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import java.util.concurrent.TimeUnit;

/** Produces the single MongoDB client of the application and its database handle. */
@ApplicationScoped
public class MongoProducer {

    private static final long SERVER_SELECTION_TIMEOUT_SECONDS = 5;

    @Produces
    @ApplicationScoped
    public MongoClient mongoClient(AppConfig config) {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applicationName("birdy")
                .applyConnectionString(new ConnectionString(config.mongoUri()))
                .applyToClusterSettings(cluster ->
                        cluster.serverSelectionTimeout(SERVER_SELECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .build();
        return MongoClients.create(settings);
    }

    @Produces
    @ApplicationScoped
    public MongoDatabase mongoDatabase(MongoClient client, AppConfig config) {
        return client.getDatabase(config.mongoDatabase());
    }

    public void close(@Disposes MongoClient client) {
        client.close();
    }
}
