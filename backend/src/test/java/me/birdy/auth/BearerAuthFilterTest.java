package me.birdy.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class BearerAuthFilterTest {

    private static final String TOKEN = new Tokens().newToken();

    @Test
    void theTokenOfABearerHeaderIsExtracted() {
        assertEquals(Optional.of(TOKEN), BearerAuthFilter.bearerToken("Bearer " + TOKEN));
    }

    @Test
    void theSchemeIsCaseInsensitiveAndSurroundingSpacesAreIgnored() {
        assertEquals(Optional.of(TOKEN), BearerAuthFilter.bearerToken("bearer " + TOKEN));
        assertEquals(Optional.of(TOKEN), BearerAuthFilter.bearerToken("BEARER   " + TOKEN + " "));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Bearer", "Bearer ", "Basic dXNlcjpwYXNz", "Bearer too-short", "Token abc"})
    void headersThatCannotCarryATokenAreRejected(String header) {
        assertEquals(Optional.empty(), BearerAuthFilter.bearerToken(header));
    }

    @Test
    void aTokenOfTheWrongLengthIsRejectedBeforeAnyLookup() {
        assertEquals(Optional.empty(), BearerAuthFilter.bearerToken("Bearer " + TOKEN + "x"));
        assertEquals(Optional.empty(), BearerAuthFilter.bearerToken("Bearer " + TOKEN.substring(1)));
        assertEquals(Optional.empty(), BearerAuthFilter.bearerToken(TOKEN));
    }
}
