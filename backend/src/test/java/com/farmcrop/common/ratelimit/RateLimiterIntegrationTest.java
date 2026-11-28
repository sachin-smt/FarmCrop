package com.farmcrop.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RateLimiterIntegrationTest {

    @Autowired
    private RateLimiter limiter;

    @Autowired
    private RateLimitRepository keys;

    @Autowired
    private RateLimitHitRepository hits;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    private static String key() {
        return "test:" + UUID.randomUUID();
    }

    @Test
    void allowsExactlyMaxWithinTheWindowAndSaysHowLongToWait() {
        String key = key();
        for (int i = 0; i < 3; i++) assertThat(limiter.tryAcquire(key, 3, Duration.ofMinutes(1))).isEmpty();
        Optional<Duration> wait = limiter.tryAcquire(key, 3, Duration.ofMinutes(1));
        assertThat(wait).isPresent();
        assertThat(wait.get()).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(1));
        assertThat(limiter.count(key, Duration.ofMinutes(1))).isEqualTo(3); // refused requests are not counted
    }

    @Test
    void concurrentRequestsCannotExceedTheLimit() throws Exception {
        String key = key();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                results.add(pool.submit(() -> { start.await(); return limiter.tryAcquire(key, 5, Duration.ofMinutes(1)).isEmpty(); }));
            }
            start.countDown();
            int allowed = 0;
            for (Future<Boolean> result : results) if (result.get()) allowed++;
            assertThat(allowed).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void anotherInstanceSharingTheDatabaseSeesTheSameCounts() {
        // Simulates a second backend instance (or a restart): a fresh limiter object over the same database.
        RateLimiter otherInstance = new RateLimiter(keys, hits, Clock.systemUTC(), transactionManager);
        String key = key();
        assertThat(limiter.tryAcquire(key, 2, Duration.ofMinutes(1))).isEmpty();
        assertThat(otherInstance.tryAcquire(key, 2, Duration.ofMinutes(1))).isEmpty();
        assertThat(limiter.tryAcquire(key, 2, Duration.ofMinutes(1))).isPresent();
        assertThat(otherInstance.tryAcquire(key, 2, Duration.ofMinutes(1))).isPresent();
    }

    @Test
    void hitsCountUnconditionallyAndResetClearsThem() {
        String key = key();
        assertThat(limiter.hit(key, Duration.ofMinutes(15))).isEqualTo(1);
        assertThat(limiter.hit(key, Duration.ofMinutes(15))).isEqualTo(2);
        limiter.reset(key);
        assertThat(limiter.count(key, Duration.ofMinutes(15))).isZero();
    }

    @Test
    void keysAreStoredOnlyAsHashesAndExpiredRowsArePurged() {
        String key = "register:203.0.113.77:" + UUID.randomUUID();
        limiter.tryAcquire(key, 5, Duration.ofMillis(1));
        String hash = RateLimiter.hash(key);
        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rate_limit_keys WHERE key_hash = ?", Integer.class, hash)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rate_limit_keys WHERE key_hash LIKE '%203.0.113.77%'", Integer.class)).isZero();

        // The 1 ms window has passed: the hit is expired and purge removes it.
        jdbc.update("UPDATE rate_limit_hits SET expires_at = TIMESTAMPADD(MINUTE, -1, expires_at) WHERE key_hash = ?", hash);
        limiter.purge();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rate_limit_hits WHERE key_hash = ?", Integer.class, hash)).isZero();
    }
}
