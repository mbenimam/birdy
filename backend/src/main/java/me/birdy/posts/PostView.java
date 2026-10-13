package me.birdy.posts;

import java.time.Instant;
import me.birdy.users.UserRef;

public record PostView(
        String id,
        UserRef author,
        String content,
        Instant createdAt,
        long likeCount,
        boolean likedByMe,
        long commentCount,
        boolean ownedByMe) {
}
