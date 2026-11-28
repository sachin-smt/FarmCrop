package com.farmcrop.common.ratelimit;

import com.farmcrop.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Function;

/**
 * Persistent, shared rate limiting backed by MySQL (V16), with exact sliding windows.
 *
 * <ul>
 *   <li>Shared by every backend instance and kept across restarts (unlike in-memory counters).</li>
 *   <li>Race-free: a key's anchor row is locked while its requests are counted.</li>
 *   <li>Each operation commits in its own short transaction ({@code REQUIRES_NEW}), so a counted attempt stays
 *       counted even if the calling request rolls back.</li>
 *   <li>Keys are stored as SHA-256 hashes; no raw IPs or emails. Expired rows are purged hourly.</li>
 * </ul>
 */
@Service
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final RateLimitRepository keys;
    private final RateLimitHitRepository hits;
    private final Clock clock;
    private final TransactionTemplate newTransaction;

    public RateLimiter(RateLimitRepository keys, RateLimitHitRepository hits, Clock clock, PlatformTransactionManager transactionManager) {
        this.keys = keys;
        this.hits = hits;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Counts one request for {@code key}; throws 429 if {@code max} were already made within {@code window}. */
    public void acquire(String key, int max, Duration window) {
        tryAcquire(key, max, window).ifPresent(wait -> {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS",
                    "Too many requests. Please wait a little and try again.");
        });
    }

    /**
     * Counts one request if fewer than {@code max} were made within {@code window}.
     * @return empty if allowed, otherwise how long until the next request would be allowed.
     */
    public Optional<Duration> tryAcquire(String key, int max, Duration window) {
        return locked(key, hash -> {
            Instant now = clock.instant();
            Instant since = now.minus(window);
            if (keys.countSince(hash, since) >= max) {
                Instant oldest = keys.oldestSince(hash, since);
                Duration wait = oldest == null ? window : Duration.between(now, oldest.plus(window));
                return Optional.of(wait.isNegative() ? Duration.ZERO : wait);
            }
            hits.save(new RateLimitHit(hash, now, now.plus(window)));
            return Optional.<Duration>empty();
        });
    }

    /** Counts one event unconditionally and returns how many happened within {@code window} (including this one). */
    public int hit(String key, Duration window) {
        return locked(key, hash -> {
            Instant now = clock.instant();
            hits.saveAndFlush(new RateLimitHit(hash, now, now.plus(window)));
            return (int) keys.countSince(hash, now.minus(window));
        });
    }

    /** How many events were counted for {@code key} within {@code window}. */
    public int count(String key, Duration window) {
        Integer result = newTransaction.execute(status -> (int) keys.countSince(hash(key), clock.instant().minus(window)));
        return result == null ? 0 : result;
    }

    /** Forgets all events for {@code key} (e.g. after a successful login). */
    public void reset(String key) {
        newTransaction.executeWithoutResult(status -> keys.deleteHits(hash(key)));
    }

    /** Housekeeping: drop expired hits and keys idle for a day. */
    @Scheduled(cron = "0 7 * * * *")
    public void purge() {
        Instant now = clock.instant();
        Integer deleted = newTransaction.execute(status -> keys.deleteExpiredHits(now) + keys.deleteIdleKeys(now.minus(Duration.ofDays(1))));
        if (deleted != null && deleted > 0) log.debug("Purged {} rate-limit rows", deleted);
    }

    /** Runs {@code body} in a new transaction while holding the lock on {@code key}'s anchor row. */
    private <T> T locked(String key, Function<String, T> body) {
        String hash = hash(key);
        ensureAnchor(hash);
        T result = newTransaction.execute(status -> {
            keys.lock(hash).orElseThrow(() -> new IllegalStateException("Rate-limit key vanished"));
            return body.apply(hash);
        });
        if (result == null) throw new IllegalStateException("Rate-limit operation produced no result");
        return result;
    }

    private void ensureAnchor(String hash) {
        try {
            newTransaction.executeWithoutResult(status -> {
                if (!keys.existsByKeyHash(hash)) keys.saveAndFlush(new RateLimitKey(hash, clock.instant()));
            });
        } catch (DataIntegrityViolationException alreadyCreatedConcurrently) {
            // Another request created it first; fine.
        }
    }

    static String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
