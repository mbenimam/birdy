package me.birdy.users;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

/** Persistence of follow links. Callers provide the transaction. */
@ApplicationScoped
public class FollowRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Records that {@code followerId} follows {@code followedId}, unless the link already exists. One atomic
     * statement, so two simultaneous requests for the same link cannot collide on the primary key.
     */
    public void insertIfAbsent(long followerId, long followedId) {
        entityManager.createNativeQuery(
                        "INSERT IGNORE INTO follows (follower_id, followed_id, created_at)"
                                + " VALUES (?1, ?2, UTC_TIMESTAMP(3))")
                .setParameter(1, followerId)
                .setParameter(2, followedId)
                .executeUpdate();
    }

    /** Returns 1 when {@code followerId} follows {@code followedId}, 0 otherwise. */
    public long count(long followerId, long followedId) {
        return entityManager.createQuery(
                        "select count(f) from Follow f where f.followerId = :followerId and f.followedId = :followedId",
                        Long.class)
                .setParameter("followerId", followerId)
                .setParameter("followedId", followedId)
                .getSingleResult();
    }

    public void delete(long followerId, long followedId) {
        entityManager.createQuery(
                        "delete from Follow f where f.followerId = :followerId and f.followedId = :followedId")
                .setParameter("followerId", followerId)
                .setParameter("followedId", followedId)
                .executeUpdate();
    }

    public long countFollowing(long userId) {
        return entityManager.createQuery("select count(f) from Follow f where f.followerId = :userId", Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    public long countFollowers(long userId) {
        return entityManager.createQuery("select count(f) from Follow f where f.followedId = :userId", Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    public List<Long> followedIds(long userId) {
        return entityManager.createQuery("select f.followedId from Follow f where f.followerId = :userId", Long.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public List<User> following(long userId) {
        return entityManager.createQuery(
                        "select u from User u where u.id in"
                                + " (select f.followedId from Follow f where f.followerId = :userId)"
                                + " order by u.username", User.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public List<User> followers(long userId) {
        return entityManager.createQuery(
                        "select u from User u where u.id in"
                                + " (select f.followerId from Follow f where f.followedId = :userId)"
                                + " order by u.username", User.class)
                .setParameter("userId", userId)
                .getResultList();
    }
}
