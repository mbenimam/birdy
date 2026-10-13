package me.birdy.users;

/** A reference to a user as embedded in API answers: the author of a post, a comment or a chat message. */
public record UserRef(long id, String username) {
}
