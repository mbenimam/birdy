package me.birdy.auth;

/** The caller of an authenticated request and the digest of the session token that was presented. */
public record AuthenticatedUser(long id, String username, String sessionDigest) {
}
