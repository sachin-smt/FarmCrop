package com.farmcrop.auth.service;

import com.farmcrop.common.exception.ApiException;
import com.farmcrop.security.SecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

/**
 * Throttles password guessing: N failures per identifier (and 4N per client IP) within a sliding
 * window block further attempts with HTTP 429 until the window passes.
 * In-memory and synchronized (login traffic is low), so it is per application instance; a shared store is needed if the API is scaled out.
 */
@Service
public class LoginAttemptService {

    private static final int IP_MULTIPLIER = 4;

    private final Map<String, Deque<Instant>> failures = new HashMap<>();
    private final SecurityProperties properties;
    private final Clock clock;

    public LoginAttemptService(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized void assertAllowed(String identifier, String clientIp) {
        if (recentFailures(identifierKey(identifier)) >= properties.loginMaxFailures()
                || recentFailures(ipKey(clientIp)) >= properties.loginMaxFailures() * IP_MULTIPLIER) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed sign-in attempts. Please wait a few minutes and try again.");
        }
    }

    public synchronized void recordFailure(String identifier, String clientIp) {
        Instant now = clock.instant();
        for (String key : new String[]{identifierKey(identifier), ipKey(clientIp)}) {
            Deque<Instant> deque = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
            prune(deque, now);
            deque.addLast(now);
        }
    }

    public synchronized void recordSuccess(String identifier) {
        failures.remove(identifierKey(identifier));
    }

    private int recentFailures(String key) {
        Deque<Instant> deque = failures.get(key);
        if (deque == null) return 0;
        prune(deque, clock.instant());
        if (deque.isEmpty()) {
            failures.remove(key);
            return 0;
        }
        return deque.size();
    }

    private void prune(Deque<Instant> deque, Instant now) {
        Instant cutoff = now.minus(properties.loginFailureWindow());
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
            deque.pollFirst();
        }
    }

    private static String identifierKey(String identifier) {
        return "id:" + (identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String clientIp) {
        return "ip:" + (clientIp == null ? "unknown" : clientIp);
    }
}
