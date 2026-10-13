package me.birdy.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AppConfigTest {

    private static AppConfig config(Map<String, String> environment) {
        return new AppConfig(environment::get);
    }

    @Test
    void mongoSettingsAreReadFromTheEnvironment() {
        AppConfig config = config(Map.of("BIRDY_MONGO_URI", " mongodb://mongo:27017 ", "BIRDY_MONGO_DB", "birdy"));

        assertEquals("mongodb://mongo:27017", config.mongoUri());
        assertEquals("birdy", config.mongoDatabase());
    }

    @Test
    void aMissingRequiredVariableIsNamedInTheError() {
        AppConfig config = config(Map.of("BIRDY_MONGO_URI", " "));

        assertTrue(assertThrows(IllegalStateException.class, config::mongoUri).getMessage()
                .contains("BIRDY_MONGO_URI"));
        assertTrue(assertThrows(IllegalStateException.class, config::mongoDatabase).getMessage()
                .contains("BIRDY_MONGO_DB"));
    }

    @Test
    void sessionSettingsHaveDefaults() {
        AppConfig config = config(Map.of());

        assertEquals(Duration.ofDays(7), config.sessionTtl());
        assertEquals(Duration.ofMinutes(15), config.sessionCleanupInterval());
    }

    @Test
    void sessionSettingsCanBeOverridden() {
        AppConfig config = config(Map.of("BIRDY_SESSION_TTL_HOURS", "12", "BIRDY_SESSION_CLEANUP_SECONDS", " 30 "));

        assertEquals(Duration.ofHours(12), config.sessionTtl());
        assertEquals(Duration.ofSeconds(30), config.sessionCleanupInterval());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "soon", "1.5"})
    void aSessionLifetimeThatIsNotAPositiveIntegerIsRefused(String value) {
        AppConfig config = config(Map.of("BIRDY_SESSION_TTL_HOURS", value));

        assertTrue(assertThrows(IllegalStateException.class, config::sessionTtl).getMessage()
                .contains("BIRDY_SESSION_TTL_HOURS"));
    }
}
