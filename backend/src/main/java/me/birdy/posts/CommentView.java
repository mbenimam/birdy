package me.birdy.posts;

import java.time.Instant;
import me.birdy.users.UserRef;

public record CommentView(String id, UserRef author, String content, Instant createdAt, boolean ownedByMe) {
}
