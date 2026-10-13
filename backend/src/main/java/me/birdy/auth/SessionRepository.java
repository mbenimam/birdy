package me.birdy.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Optional;

/** Persistence of login sessions. Callers provide the transaction. */
@ApplicationScoped
public class SessionRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public void insert(UserSession session) {
        entityManager.persist(session);
    }

    public Optional<UserSession> findByDigest(String tokenDigest) {
        return Optional.ofNullable(entityManager.find(UserSession.class, tokenDigest));
    }

    public void deleteByDigest(String tokenDigest) {
        entityManager.createQuery("delete from UserSession s where s.tokenDigest = :tokenDigest")
                .setParameter("tokenDigest", tokenDigest)
                .executeUpdate();
    }

    /** Deletes the sessions that expired at or before {@code now} and returns how many there were. */
    public long deleteExpired(Instant now) {
        return entityManager.createQuery("delete from UserSession s where s.expiresAt <= :now")
                .setParameter("now", now)
                .executeUpdate();
    }
}
