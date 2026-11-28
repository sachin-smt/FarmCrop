package com.farmcrop.common.ratelimit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RateLimitHitRepository extends JpaRepository<RateLimitHit, Long> {
}
