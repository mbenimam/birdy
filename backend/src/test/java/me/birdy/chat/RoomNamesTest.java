package me.birdy.chat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RoomNamesTest {

    @ParameterizedTest
    @ValueSource(strings = {"a", "lobby", "room-42", "0", "---", "abcdefghijklmnopqrstuvwxyz012345"})
    void lowerCaseLettersDigitsAndHyphensUpToThirtyTwoCharactersAreValid(String room) {
        assertTrue(RoomNames.isValid(room));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "Lobby", "with space", "under_score", "abcdefghijklmnopqrstuvwxyz0123456", "café",
            "a/b", "room\n"})
    void everythingElseIsInvalid(String room) {
        assertFalse(RoomNames.isValid(room));
    }
}
