package com.farmcrop.common.ratelimit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One counted request for a rate-limited key (V16). */
@Entity
@Table(name = "rate_limit_hits")
public class RateLimitHit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "hit_at", nullable = false)
    private Instant hitAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected RateLimitHit() {
    }

    public RateLimitHit(String keyHash, Instant hitAt, Instant expiresAt) {
        this.keyHash = keyHash;
        this.hitAt = hitAt;
        this.expiresAt = expiresAt;
    }

    public Instant getHitAt() {
        return hitAt;
    }
}
