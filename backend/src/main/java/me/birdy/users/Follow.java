package me.birdy.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * The fact that one user (the follower) follows another (the followed). Rows are written by
 * {@link FollowRepository#insertIfAbsent}; the entity exists for queries.
 */
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

    protected Follow() {
    }
}
