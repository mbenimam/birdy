package me.birdy.users;

/** Composite primary key of {@link Follow}. */
public record FollowId(long followerId, long followedId) {
}
