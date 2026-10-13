package me.birdy.users;

import jakarta.data.repository.Insert;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import java.util.List;

@Repository
public interface FollowRepository {

    @Insert
    void insert(Follow follow);

    @Query("select count(this) from Follow where followerId = :followerId and followedId = :followedId")
    long count(long followerId, long followedId);

    @Query("delete from Follow where followerId = :followerId and followedId = :followedId")
    void delete(long followerId, long followedId);

    @Query("select count(this) from Follow where followerId = :userId")
    long countFollowing(long userId);

    @Query("select count(this) from Follow where followedId = :userId")
    long countFollowers(long userId);

    @Query("select followedId from Follow where followerId = :userId")
    List<Long> followedIds(long userId);

    @Query("select u from User u where u.id in (select f.followedId from Follow f where f.followerId = :userId)"
            + " order by u.username")
    List<User> following(long userId);

    @Query("select u from User u where u.id in (select f.followerId from Follow f where f.followedId = :userId)"
            + " order by u.username")
    List<User> followers(long userId);
}
