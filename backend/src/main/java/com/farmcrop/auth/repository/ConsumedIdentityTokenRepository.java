package com.farmcrop.auth.repository;

import com.farmcrop.auth.entity.ConsumedIdentityToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ConsumedIdentityTokenRepository extends JpaRepository<ConsumedIdentityToken, Long> {

    boolean existsByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from ConsumedIdentityToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
