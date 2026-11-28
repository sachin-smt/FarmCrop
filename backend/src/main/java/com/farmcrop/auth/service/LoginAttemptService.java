package com.farmcrop.auth.service;

import com.farmcrop.common.exception.ApiException;
import com.farmcrop.common.ratelimit.RateLimiter;
import com.farmcrop.security.SecurityProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Throttles password guessing: N failures per identifier (and 4N per client IP) within a sliding window block
 * further attempts with HTTP 429 until the window passes. Counts live in the shared database store
 * ({@link RateLimiter}), so they apply across all backend instances and survive restarts.
 */
@Service
public class LoginAttemptService {

    private static final int IP_MULTIPLIER = 4;

    private final RateLimiter rateLimiter;
    private final SecurityProperties properties;

    public LoginAttemptService(RateLimiter rateLimiter, SecurityProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    public void assertAllowed(String identifier, String clientIp) {
        if (rateLimiter.count(identifierKey(identifier), properties.loginFailureWindow()) >= properties.loginMaxFailures()
                || rateLimiter.count(ipKey(clientIp), properties.loginFailureWindow()) >= properties.loginMaxFailures() * IP_MULTIPLIER) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed sign-in attempts. Please wait a few minutes and try again.");
        }
    }

    public void recordFailure(String identifier, String clientIp) {
        rateLimiter.hit(identifierKey(identifier), properties.loginFailureWindow());
        rateLimiter.hit(ipKey(clientIp), properties.loginFailureWindow());
    }

    public void recordSuccess(String identifier) {
        rateLimiter.reset(identifierKey(identifier));
    }

    private static String identifierKey(String identifier) {
        return "login-failures:id:" + (identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT));
    }

    private static String ipKey(String clientIp) {
        return "login-failures:ip:" + (clientIp == null ? "unknown" : clientIp);
    }
}
