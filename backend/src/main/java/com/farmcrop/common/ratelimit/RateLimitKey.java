package com.farmcrop.common.ratelimit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Lock anchor for one rate-limited key (V16). */
@Entity
@Table(name = "rate_limit_keys")
public class RateLimitKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RateLimitKey() {
    }

    public RateLimitKey(String keyHash, Instant createdAt) {
        this.keyHash = keyHash;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }
}
