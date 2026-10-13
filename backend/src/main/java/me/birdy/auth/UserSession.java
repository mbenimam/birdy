package me.birdy.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A login session, identified by the digest of its token. */
@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @Column(name = "token_digest")
    private String tokenDigest;

    @Column(name = "user_id")
    private long userId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected UserSession() {
    }

    public UserSession(String tokenDigest, long userId, Instant createdAt, Instant expiresAt) {
        this.tokenDigest = tokenDigest;
        this.userId = userId;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public long userId() {
        return userId;
    }

    /** A session is no longer usable from its expiry instant on. */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
