package me.birdy.users;

import jakarta.data.Limit;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository {

    @Insert
    void insert(User user);

    @Find
    Optional<User> findById(@By("id") long id);

    @Find
    Optional<User> findByUsername(@By("username") String username);

    @Query("select count(this) from User where username = :username or email = :email")
    long countByUsernameOrEmail(String username, String email);

    /** Users other than {@code self} whose username matches the LIKE pattern, see {@link LikePattern}. */
    @Query("from User where id <> :self and lower(username) like :pattern escape '!' order by username")
    List<User> search(long self, String pattern, Limit limit);

    @Query("update User set passwordHash = :passwordHash where id = :id")
    void updatePasswordHash(long id, String passwordHash);

    /** Sessions and follow links go with the row through the foreign keys' ON DELETE CASCADE. */
    @Query("delete from User where id = :id")
    void deleteById(long id);
}
