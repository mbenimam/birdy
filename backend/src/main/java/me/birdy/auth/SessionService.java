package me.birdy.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import me.birdy.api.ApiException;
import me.birdy.config.AppConfig;
import me.birdy.users.User;
import me.birdy.users.UserRepository;
import me.birdy.users.UserService;

/** Login, token authentication, logout and expiry of sessions. */
@ApplicationScoped
public class SessionService {

    private static final String BAD_CREDENTIALS = "Invalid username or password";

    @Inject
    private SessionRepository sessions;

    @Inject
    private UserRepository users;

    @Inject
    private UserService userService;

    @Inject
    private PasswordHasher hasher;

    @Inject
    private Tokens tokens;

    @Inject
    private AppConfig config;

    @Inject
    private Clock clock;

    /** Verified in place of a real hash when the username is unknown, so both failures take the same time. */
    private String decoyHash;

    @PostConstruct
    void init() {
        decoyHash = hasher.hash(tokens.newToken());
    }

    /** Opens a session; an unknown username and a wrong password are indistinguishable for the caller. */
    @Transactional
    public SessionView login(LoginRequest request) {
        Optional<User> found = users.findByUsername(request.username());
        boolean matches = hasher.verify(request.password(), found.map(User::passwordHash).orElse(decoyHash));
        if (found.isEmpty() || !matches) {
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        User user = found.get();
        if (hasher.needsRehash(user.passwordHash())) {
            users.updatePasswordHash(user.id(), hasher.hash(request.password()));
        }
        String token = tokens.newToken();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Instant expiresAt = now.plus(config.sessionTtl());
        sessions.insert(new UserSession(tokens.digest(token), user.id(), now, expiresAt));
        return new SessionView(token, expiresAt, userService.ownProfile(user));
    }

    /** Resolves a presented token to its user; an expired session is removed and rejected. */
    @Transactional
    public Optional<AuthenticatedUser> authenticate(String token) {
        String digest = tokens.digest(token);
        Optional<UserSession> session = sessions.findByDigest(digest);
        if (session.isEmpty()) {
            return Optional.empty();
        }
        if (session.get().isExpiredAt(clock.instant())) {
            sessions.deleteByDigest(digest);
            return Optional.empty();
        }
        return users.findById(session.get().userId())
                .map(user -> new AuthenticatedUser(user.id(), user.username(), digest));
    }

    @Transactional
    public void logout(AuthenticatedUser user) {
        sessions.deleteByDigest(user.sessionDigest());
    }

    /** Deletes every expired session and returns how many were removed. */
    @Transactional
    public long purgeExpired() {
        return sessions.deleteExpired(clock.instant());
    }
}
