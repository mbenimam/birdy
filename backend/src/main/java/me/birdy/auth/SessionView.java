package me.birdy.auth;

import java.time.Instant;
import me.birdy.users.UserView;

/** Answer to a successful login. This is the only time the token itself leaves the server. */
public record SessionView(String token, Instant expiresAt, UserView user) {
}
