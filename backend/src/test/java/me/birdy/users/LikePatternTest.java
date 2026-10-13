package me.birdy.users;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LikePatternTest {

    @Test
    void theTextIsLowerCasedTrimmedAndSurroundedByWildcards() {
        assertEquals("%ali%", LikePattern.containing("  ALi "));
    }

    @Test
    void anAbsentOrEmptyTextMatchesEverything() {
        assertEquals("%%", LikePattern.containing(null));
        assertEquals("%%", LikePattern.containing("   "));
    }

    @Test
    void wildcardsAndTheEscapeCharacterAreTakenLiterally() {
        assertEquals("%a!_b%", LikePattern.containing("a_b"));
        assertEquals("%100!%%", LikePattern.containing("100%"));
        assertEquals("%wow!!%", LikePattern.containing("wow!"));
        assertEquals("%!!!%!_%", LikePattern.containing("!%_"));
    }
}
