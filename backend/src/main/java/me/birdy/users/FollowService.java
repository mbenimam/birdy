package me.birdy.users;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.birdy.api.ApiException;

/** Follow links between users. */
@ApplicationScoped
public class FollowService {

    @Inject
    private FollowRepository follows;

    @Inject
    private UserRepository users;

    @Inject
    private UserService userService;

    @Inject
    private Clock clock;

    /** Makes {@code followerId} follow {@code username}; doing it twice changes nothing. */
    @Transactional
    public void follow(long followerId, String username) {
        User target = userService.require(username);
        if (target.id() == followerId) {
            throw ApiException.invalid("username", "you cannot follow yourself");
        }
        if (follows.count(followerId, target.id()) == 0) {
            follows.insert(new Follow(followerId, target.id(), clock.instant()));
        }
    }

    /** Removes the link if it exists; an unknown username or a missing link is not an error. */
    @Transactional
    public void unfollow(long followerId, String username) {
        Optional<User> target = users.findByUsername(username);
        target.ifPresent(user -> follows.delete(followerId, user.id()));
    }

    @Transactional
    public List<UserSummary> following(String username, long viewerId) {
        return userService.summaries(follows.following(userService.require(username).id()), viewerId);
    }

    @Transactional
    public List<UserSummary> followers(String username, long viewerId) {
        return userService.summaries(follows.followers(userService.require(username).id()), viewerId);
    }

    /** The ids whose posts make up the home feed of a user: the user and everyone they follow. */
    @Transactional
    public List<Long> homeFeedAuthors(long userId) {
        List<Long> authors = new ArrayList<>(follows.followedIds(userId));
        authors.add(userId);
        return authors;
    }
}
