package me.birdy.users;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

/** Persistence of users. Callers provide the transaction. */
@ApplicationScoped
public class UserRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /** Inserts the user immediately, so that its generated id is available on return. */
    public void insert(User user) {
        entityManager.persist(user);
        entityManager.flush();
    }

    public Optional<User> findById(long id) {
        return Optional.ofNullable(entityManager.find(User.class, id));
    }

    public Optional<User> findByUsername(String username) {
        return entityManager.createQuery("select u from User u where u.username = :username", User.class)
                .setParameter("username", username)
                .getResultStream()
                .findFirst();
    }

    public long countByUsernameOrEmail(String username, String email) {
        return entityManager.createQuery(
                        "select count(u) from User u where u.username = :username or u.email = :email", Long.class)
                .setParameter("username", username)
                .setParameter("email", email)
                .getSingleResult();
    }

    /** Users other than {@code self} whose username matches the LIKE pattern, see {@link LikePattern}. */
    public List<User> search(long self, String pattern, int limit) {
        return entityManager.createQuery(
                        "select u from User u where u.id <> :self and lower(u.username) like :pattern escape '!'"
                                + " order by u.username", User.class)
                .setParameter("self", self)
                .setParameter("pattern", pattern)
                .setMaxResults(limit)
                .getResultList();
    }

    public void updatePasswordHash(long id, String passwordHash) {
        entityManager.createQuery("update User u set u.passwordHash = :passwordHash where u.id = :id")
                .setParameter("passwordHash", passwordHash)
                .setParameter("id", id)
                .executeUpdate();
    }

    /** Sessions and follow links go with the row through the foreign keys' ON DELETE CASCADE. */
    public void deleteById(long id) {
        entityManager.createQuery("delete from User u where u.id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }
}
