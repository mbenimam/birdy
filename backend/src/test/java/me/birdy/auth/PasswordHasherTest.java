package me.birdy.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordHasherTest {

    private static final String PASSWORD = "correct horse battery staple";

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashStoresTheSchemeAndItsParametersWithTheHash() {
        String[] parts = hasher.hash(PASSWORD).split("\\$");

        assertEquals(4, parts.length);
        assertEquals("pbkdf2-sha256", parts[0]);
        assertTrue(Integer.parseInt(parts[1]) >= 600_000, "at least 600,000 iterations");
        assertEquals(16, Base64.getDecoder().decode(parts[2]).length, "16-byte salt");
        assertEquals(32, Base64.getDecoder().decode(parts[3]).length, "256-bit hash");
    }

    @Test
    void hashNeverContainsThePassword() {
        assertFalse(hasher.hash(PASSWORD).contains(PASSWORD));
    }

    @Test
    void verifyAcceptsTheRightPassword() {
        assertTrue(hasher.verify(PASSWORD, hasher.hash(PASSWORD)));
    }

    @Test
    void verifyRejectsAWrongPassword() {
        String stored = hasher.hash(PASSWORD);

        assertFalse(hasher.verify("correct horse battery stapl", stored));
        assertFalse(hasher.verify(PASSWORD + " ", stored));
        assertFalse(hasher.verify(PASSWORD.toUpperCase(), stored));
        assertFalse(hasher.verify("", stored));
        assertFalse(hasher.verify(null, stored));
    }

    @Test
    void everyHashGetsItsOwnSalt() {
        String first = hasher.hash(PASSWORD);
        String second = hasher.hash(PASSWORD);

        assertNotEquals(first.split("\\$")[2], second.split("\\$")[2]);
        assertNotEquals(first, second);
        assertTrue(hasher.verify(PASSWORD, first));
        assertTrue(hasher.verify(PASSWORD, second));
    }

    @Test
    void verifyComputesStandardPbkdf2HmacSha256() {
        // RFC 7914 section 11 test vector: P = "passwd", S = "salt", c = 1, dkLen = 64 (first 32 bytes used here).
        byte[] expected = HexFormat.of().parseHex("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc");
        Base64.Encoder base64 = Base64.getEncoder().withoutPadding();
        String stored = "pbkdf2-sha256$1$" + base64.encodeToString("salt".getBytes()) + "$"
                + base64.encodeToString(expected);

        assertTrue(hasher.verify("passwd", stored));
        assertFalse(hasher.verify("passwe", stored));
    }

    @Test
    void verifyRejectsAHashThatWasTamperedWith() {
        String[] parts = hasher.hash(PASSWORD).split("\\$");
        byte[] hash = Base64.getDecoder().decode(parts[3]);
        hash[0] ^= 1;
        parts[3] = Base64.getEncoder().withoutPadding().encodeToString(hash);

        assertFalse(hasher.verify(PASSWORD, String.join("$", parts)));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "plain-text-password",
            "pbkdf2-sha256$600000$c2FsdA",
            "pbkdf2-sha1$600000$c2FsdHNhbHRzYWx0c2FsdA$aGFzaA",
            "pbkdf2-sha256$many$c2FsdHNhbHRzYWx0c2FsdA$aGFzaA",
            "pbkdf2-sha256$0$c2FsdHNhbHRzYWx0c2FsdA$aGFzaA",
            "pbkdf2-sha256$600000$***$aGFzaA",
            "pbkdf2-sha256$600000$$"})
    void verifyRejectsMalformedStoredValuesWithoutFailing(String stored) {
        assertFalse(hasher.verify(PASSWORD, stored));
        assertTrue(hasher.needsRehash(stored));
    }

    @Test
    void hashesMadeWithFewerIterationsStillVerifyAndAreFlaggedForRehash() {
        String weaker = hasher.hash(PASSWORD, 1_000);

        assertTrue(hasher.verify(PASSWORD, weaker), "the parameters stored with the hash are used");
        assertFalse(hasher.verify("another password", weaker));
        assertTrue(hasher.needsRehash(weaker));
    }

    @Test
    void currentHashesDoNotNeedRehash() {
        assertFalse(hasher.needsRehash(hasher.hash(PASSWORD)));
    }
}
