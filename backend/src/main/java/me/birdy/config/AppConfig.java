package me.birdy.config;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.function.UnaryOperator;

/** Application settings, all read from environment variables. */
@ApplicationScoped
public class AppConfig {

    static final String MONGO_URI = "BIRDY_MONGO_URI";
    static final String MONGO_DB = "BIRDY_MONGO_DB";
    static final String SESSION_TTL_HOURS = "BIRDY_SESSION_TTL_HOURS";
    static final String SESSION_CLEANUP_SECONDS = "BIRDY_SESSION_CLEANUP_SECONDS";

    private static final long DEFAULT_SESSION_TTL_HOURS = 24 * 7;
    private static final long DEFAULT_SESSION_CLEANUP_SECONDS = 15 * 60;

    private final UnaryOperator<String> environment;

    public AppConfig() {
        this(System::getenv);
    }

    AppConfig(UnaryOperator<String> environment) {
        this.environment = environment;
    }

    public String mongoUri() {
        return required(MONGO_URI);
    }

    public String mongoDatabase() {
        return required(MONGO_DB);
    }

    /** How long a session token stays valid after login. */
    public Duration sessionTtl() {
        return Duration.ofHours(positiveLong(SESSION_TTL_HOURS, DEFAULT_SESSION_TTL_HOURS));
    }

    /** Interval between two purges of expired sessions. */
    public Duration sessionCleanupInterval() {
        return Duration.ofSeconds(positiveLong(SESSION_CLEANUP_SECONDS, DEFAULT_SESSION_CLEANUP_SECONDS));
    }

    private String required(String name) {
        String value = environment.apply(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Environment variable " + name + " must be set");
        }
        return value.strip();
    }

    private long positiveLong(String name, long defaultValue) {
        String value = environment.apply(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(value.strip());
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException e) {
            // Reported below with the variable name.
        }
        throw new IllegalStateException("Environment variable " + name + " must be a positive integer");
    }
}
