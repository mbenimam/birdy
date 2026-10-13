package me.birdy.users;

import jakarta.data.Limit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.birdy.api.ApiException;
import me.birdy.auth.PasswordHasher;
import me.birdy.posts.PostStore;

/** Accounts: signup, profiles, search and deletion. */
@ApplicationScoped
public class UserService {

    /** {@code /api/users/me} designates the caller, so nobody may own this name. */
    private static final String RESERVED_USERNAME = "me";
    private static final int MAX_QUERY_LENGTH = 30;

    @Inject
    private UserRepository users;

    @Inject
    private FollowRepository follows;

    @Inject
    private PasswordHasher hasher;

    @Inject
    private PostStore posts;

    @Inject
    private Clock clock;

    @Transactional
    public UserView signup(SignupRequest request) {
        if (RESERVED_USERNAME.equalsIgnoreCase(request.username())) {
            throw ApiException.invalid("username", "is reserved");
        }
        if (users.countByUsernameOrEmail(request.username(), request.email()) > 0) {
            throw ApiException.conflict("The username or email is already taken");
        }
        users.insert(new User(request.username(), request.email(), hasher.hash(request.password()),
                request.birthday(), clock.instant().truncatedTo(ChronoUnit.MILLIS)));
        // The database assigns the id when the insert is flushed, which this query triggers.
        return UserView.own(require(request.username()), 0, 0);
    }

    /** The profile of {@code user} as its owner sees it, private properties included. */
    @Transactional
    public UserView ownProfile(User user) {
        return UserView.own(user, follows.countFollowing(user.id()), follows.countFollowers(user.id()));
    }

    @Transactional
    public UserView ownProfile(long userId) {
        return ownProfile(users.findById(userId).orElseThrow(UserService::unknownUser));
    }

    @Transactional
    public UserView profile(String username, long viewerId) {
        User user = require(username);
        boolean followedByViewer = follows.count(viewerId, user.id()) > 0;
        return UserView.other(user, follows.countFollowing(user.id()), follows.countFollowers(user.id()),
                followedByViewer);
    }

    @Transactional
    public List<UserSummary> search(String query, int limit, long viewerId) {
        if (query != null && query.strip().length() > MAX_QUERY_LENGTH) {
            throw ApiException.invalid("q", "must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        return summaries(users.search(viewerId, LikePattern.containing(query), Limit.of(limit)), viewerId);
    }

    /** Removes the account with everything it owns: MongoDB content first, then the row and its dependants. */
    @Transactional
    public void delete(long userId) {
        posts.deleteEverythingOf(userId);
        users.deleteById(userId);
    }

    /** Returns the id of the user with this username or fails with {@code 404}. */
    @Transactional
    public long idOf(String username) {
        return require(username).id();
    }

    /** Returns the user with this username or fails with {@code 404}. */
    User require(String username) {
        return users.findByUsername(username).orElseThrow(UserService::unknownUser);
    }

    /** Converts users to summaries, flagging those the viewer follows. */
    List<UserSummary> summaries(List<User> listed, long viewerId) {
        Set<Long> followed = new HashSet<>(follows.followedIds(viewerId));
        return listed.stream()
                .map(user -> new UserSummary(user.id(), user.username(), followed.contains(user.id())))
                .toList();
    }

    private static ApiException unknownUser() {
        return ApiException.notFound("User not found");
    }
}
