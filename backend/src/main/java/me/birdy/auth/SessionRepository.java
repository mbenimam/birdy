package me.birdy.auth;

import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Insert;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import java.time.Instant;
import java.util.Optional;

@Repository
public interface SessionRepository {

    @Insert
    void insert(UserSession session);

    @Find
    Optional<UserSession> findByDigest(@By("tokenDigest") String tokenDigest);

    @Query("delete from UserSession where tokenDigest = :tokenDigest")
    void deleteByDigest(String tokenDigest);

    @Query("delete from UserSession where expiresAt <= :now")
    long deleteExpired(Instant now);
}
