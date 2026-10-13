package me.birdy.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserSessionTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant EXPIRES = Instant.parse("2026-01-01T11:00:00Z");

    private final UserSession session = new UserSession("digest", 7, CREATED, EXPIRES);

    @Test
    void aSessionIsUsableUntilItsExpiry() {
        assertFalse(session.isExpiredAt(CREATED));
        assertFalse(session.isExpiredAt(EXPIRES.minusMillis(1)));
    }

    @Test
    void aSessionIsExpiredFromItsExpiryInstantOn() {
        assertTrue(session.isExpiredAt(EXPIRES));
        assertTrue(session.isExpiredAt(EXPIRES.plusSeconds(3600)));
    }
}
