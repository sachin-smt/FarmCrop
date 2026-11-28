package com.farmcrop.auth.repository;

import com.farmcrop.auth.entity.PendingRegistration;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    /** Row-locks the registration so concurrent verify/resend calls are serialized (no double consumption). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PendingRegistration p where p.email = :email")
    Optional<PendingRegistration> findByEmailForUpdate(@Param("email") String email);

    @Query("select p from PendingRegistration p where p.email = :email and p.consumedAt is null")
    Optional<PendingRegistration> findOpenByEmail(@Param("email") String email);

    /** Newest unconsumed registration using this username (usernames are not unique among pending sign-ups). */
    Optional<PendingRegistration> findFirstByUsernameAndConsumedAtIsNullOrderByUpdatedAtDesc(String username);

    @Modifying
    @Query("delete from PendingRegistration p where (p.consumedAt is not null and p.consumedAt < :consumedBefore) "
            + "or (p.consumedAt is null and p.otpExpiresAt < :expiredBefore)")
    int purge(@Param("consumedBefore") Instant consumedBefore, @Param("expiredBefore") Instant expiredBefore);
}
