package me.birdy.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/** The fact that one user (the follower) follows another (the followed). */
@Entity
@Table(name = "follows")
@IdClass(FollowId.class)
public class Follow {

    @Id
    @Column(name = "follower_id")
    private long followerId;

    @Id
    @Column(name = "followed_id")
    private long followedId;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Follow() {
    }

    public Follow(long followerId, long followedId, Instant createdAt) {
        this.followerId = followerId;
        this.followedId = followedId;
        this.createdAt = createdAt;
    }
}
