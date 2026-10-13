package me.birdy.posts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;
import me.birdy.api.Validation;
import org.junit.jupiter.api.Test;

class ContentRequestsTest {

    @Test
    void postContentIsTrimmed() {
        assertEquals("hello world", new CreatePostRequest(" \n hello world\t ").content());
        assertNull(new CreatePostRequest(null).content());
    }

    @Test
    void postContentMustNotBeBlank() {
        assertEquals(Set.of("content"), Validation.invalidFields(new CreatePostRequest(null)));
        assertEquals(Set.of("content"), Validation.invalidFields(new CreatePostRequest("")));
        assertEquals(Set.of("content"), Validation.invalidFields(new CreatePostRequest(" \n\t ")));
    }

    @Test
    void postContentIsLimitedToFiveHundredCharactersAfterTrimming() {
        assertEquals(Set.of(), Validation.invalidFields(new CreatePostRequest("x")));
        assertEquals(Set.of(), Validation.invalidFields(new CreatePostRequest("x".repeat(500))));
        assertEquals(Set.of(), Validation.invalidFields(new CreatePostRequest("  " + "x".repeat(500) + "  ")));
        assertEquals(Set.of("content"), Validation.invalidFields(new CreatePostRequest("x".repeat(501))));
    }

    @Test
    void commentContentIsTrimmed() {
        assertEquals("nice", new CreateCommentRequest("  nice ").content());
        assertNull(new CreateCommentRequest(null).content());
    }

    @Test
    void commentContentMustHaveOneToThreeHundredCharacters() {
        assertEquals(Set.of("content"), Validation.invalidFields(new CreateCommentRequest(null)));
        assertEquals(Set.of("content"), Validation.invalidFields(new CreateCommentRequest("   ")));
        assertEquals(Set.of(), Validation.invalidFields(new CreateCommentRequest(" " + "x".repeat(300) + " ")));
        assertEquals(Set.of("content"), Validation.invalidFields(new CreateCommentRequest("x".repeat(301))));
    }
}
