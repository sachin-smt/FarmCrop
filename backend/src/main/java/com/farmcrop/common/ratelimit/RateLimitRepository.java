package com.farmcrop.common.ratelimit;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RateLimitRepository extends JpaRepository<RateLimitKey, Long> {

    boolean existsByKeyHash(String keyHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from RateLimitKey k where k.keyHash = :keyHash")
    Optional<RateLimitKey> lock(@Param("keyHash") String keyHash);

    @Query("select count(h) from RateLimitHit h where h.keyHash = :keyHash and h.hitAt > :since")
    long countSince(@Param("keyHash") String keyHash, @Param("since") Instant since);

    @Query("select min(h.hitAt) from RateLimitHit h where h.keyHash = :keyHash and h.hitAt > :since")
    Instant oldestSince(@Param("keyHash") String keyHash, @Param("since") Instant since);

    @Modifying
    @Query("delete from RateLimitHit h where h.keyHash = :keyHash")
    int deleteHits(@Param("keyHash") String keyHash);

    @Modifying
    @Query("delete from RateLimitHit h where h.expiresAt < :now")
    int deleteExpiredHits(@Param("now") Instant now);

    @Modifying
    @Query("delete from RateLimitKey k where k.createdAt < :cutoff and not exists "
            + "(select h.id from RateLimitHit h where h.keyHash = k.keyHash)")
    int deleteIdleKeys(@Param("cutoff") Instant cutoff);
}
