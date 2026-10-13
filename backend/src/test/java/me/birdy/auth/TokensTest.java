package me.birdy.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokensTest {

    private final Tokens tokens = new Tokens();

    @Test
    void aTokenIsThirtyTwoRandomBytesInUrlSafeBase64() {
        String token = tokens.newToken();

        assertEquals(Tokens.TOKEN_LENGTH, token.length());
        assertTrue(token.matches("[A-Za-z0-9_-]+"), "usable in a header and in a URL without escaping");
        assertEquals(32, Base64.getUrlDecoder().decode(token).length);
    }

    @Test
    void tokensAreNotRepeated() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            assertTrue(seen.add(tokens.newToken()));
        }
    }

    @Test
    void digestIsTheSha256OfTheTokenInHex() {
        // FIPS 180-2 test vector.
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", tokens.digest("abc"));
    }

    @Test
    void digestIsStableAndDoesNotRevealTheToken() {
        String token = tokens.newToken();
        String digest = tokens.digest(token);

        assertEquals(digest, tokens.digest(token));
        assertEquals(64, digest.length());
        assertNotEquals(token, digest);
        assertNotEquals(digest, tokens.digest(tokens.newToken()));
    }
}
