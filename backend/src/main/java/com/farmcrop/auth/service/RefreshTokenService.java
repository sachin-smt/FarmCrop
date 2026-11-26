package com.farmcrop.auth.service;

import com.farmcrop.auth.entity.RefreshToken;
import com.farmcrop.auth.entity.RefreshToken.RevokeReason;
import com.farmcrop.auth.repository.RefreshTokenRepository;
import com.farmcrop.common.exception.ApiException;
import com.farmcrop.security.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Opaque, rotating refresh tokens. Only SHA-256 hashes are persisted. Each login starts a token
 * "family"; every refresh revokes the presented token and issues a successor in the same family.
 * Presenting a token that was already rotated (outside a short race window) means it was copied,
 * so the whole family is revoked.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;
    /** Two tabs refreshing at the same moment both present the same cookie; tolerate the loser. */
    static final Duration ROTATION_RACE_WINDOW = Duration.ofSeconds(30);

    public record Rotation(Long userId, String newRawToken) {
    }

    private final RefreshTokenRepository repository;
    private final SecurityProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, SecurityProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Starts a new session family for the user and returns the raw token for the cookie. */
    @Transactional
    public String issueNewFamily(Long userId) {
        return issue(userId, UUID.randomUUID().toString());
    }

    /**
     * Validates and rotates a refresh token. Revocations performed while rejecting a token must be
     * committed, hence {@code noRollbackFor}.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw sessionExpired();
        Instant now = clock.instant();
        RefreshToken token = repository.findByHashForUpdate(hash(rawToken)).orElseThrow(RefreshTokenService::sessionExpired);

        if (token.isRevoked()) {
            boolean benignRace = token.getRevokeReason() == RevokeReason.ROTATED
                    && token.getRevokedAt().isAfter(now.minus(ROTATION_RACE_WINDOW));
            if (!benignRace) {
                int revoked = repository.revokeFamily(token.getFamilyId(), RevokeReason.REUSE_DETECTED, now);
                log.warn("Refresh token reuse detected for user {}; revoked {} active token(s) in the family", token.getUserId(), revoked);
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, benignRace ? "REFRESH_RACE" : "SESSION_REVOKED",
                    "Your session has ended. Please sign in again.");
        }
        if (token.isExpired(now)) throw sessionExpired();

        token.revoke(RevokeReason.ROTATED, now);
        return new Rotation(token.getUserId(), issue(token.getUserId(), token.getFamilyId()));
    }

    /** Logout: revoke the presented token's whole family. Unknown tokens are ignored. */
    @Transactional
    public void revokeFamilyOf(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        repository.findByHashForUpdate(hash(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), RevokeReason.LOGOUT, clock.instant()));
    }

    @Transactional
    public void revokeAllForUser(Long userId, RevokeReason reason) {
        repository.revokeAllForUser(userId, reason, clock.instant());
    }

    /** Housekeeping: drop tokens that expired more than a day ago. */
    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
        if (deleted > 0) log.info("Purged {} expired refresh tokens", deleted);
    }

    public Duration ttl() {
        return properties.refreshTokenTtl();
    }

    private String issue(Long userId, String familyId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        repository.save(new RefreshToken(userId, hash(raw), familyId, now, now.plus(properties.refreshTokenTtl())));
        return raw;
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static ApiException sessionExpired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "Your session has expired. Please sign in again.");
    }
}
