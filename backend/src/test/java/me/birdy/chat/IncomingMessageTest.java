package me.birdy.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class IncomingMessageTest {

    @Test
    void theContentIsTrimmed() {
        assertEquals("hello", new IncomingMessage("  hello\n").validContent());
    }

    @Test
    void missingAndBlankContentIsInvalid() {
        assertNull(new IncomingMessage(null).validContent());
        assertNull(new IncomingMessage("").validContent());
        assertNull(new IncomingMessage(" \t\n").validContent());
    }

    @Test
    void theContentIsLimitedToOneThousandCharacters() {
        assertEquals(1000, new IncomingMessage("x".repeat(1000)).validContent().length());
        assertEquals(1000, new IncomingMessage(" " + "x".repeat(1000) + " ").validContent().length());
        assertNull(new IncomingMessage("x".repeat(1001)).validContent());
    }
}
