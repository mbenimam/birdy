package me.birdy.users;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A user as seen through the API. {@code email} and {@code birthday} are only set on the caller's own profile and
 * {@code followedByMe} only on somebody else's; unset properties are left out of the JSON.
 */
public record UserView(
        long id,
        String username,
        String email,
        LocalDate birthday,
        Instant createdAt,
        long followingCount,
        long followerCount,
        Boolean followedByMe) {

    static UserView own(User user, long followingCount, long followerCount) {
        return new UserView(user.id(), user.username(), user.email(), user.birthday(), user.createdAt(),
                followingCount, followerCount, null);
    }

    static UserView other(User user, long followingCount, long followerCount, boolean followedByMe) {
        return new UserView(user.id(), user.username(), null, null, user.createdAt(),
                followingCount, followerCount, followedByMe);
    }
}
