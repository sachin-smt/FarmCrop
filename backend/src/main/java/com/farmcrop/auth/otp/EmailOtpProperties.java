package com.farmcrop.auth.otp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Email OTP policy (prefix {@code farmcrop.email-otp}).
 *
 * @param hmacSecret      base64 key (at least 32 bytes) used to HMAC codes before storage; from EMAIL_OTP_HMAC_SECRET.
 * @param ttl             how long a code is valid.
 * @param resendCooldown  minimum time between two codes for the same email.
 * @param maxAttempts     wrong guesses allowed per code.
 * @param maxResendsPerHour additional codes allowed per email per hour.
 */
@ConfigurationProperties(prefix = "farmcrop.email-otp")
public record EmailOtpProperties(String hmacSecret, Duration ttl, Duration resendCooldown, Integer maxAttempts,
                                 Integer maxResendsPerHour) {

    public EmailOtpProperties {
        hmacSecret = hmacSecret == null ? "" : hmacSecret.trim();
        if (ttl == null) ttl = Duration.ofMinutes(10);
        if (resendCooldown == null) resendCooldown = Duration.ofSeconds(60);
        if (maxAttempts == null) maxAttempts = 5;
        if (maxResendsPerHour == null) maxResendsPerHour = 3;
    }

    @Override
    public String toString() {
        return "EmailOtpProperties[hmacSecret=***, ttl=" + ttl + ", resendCooldown=" + resendCooldown
                + ", maxAttempts=" + maxAttempts + ", maxResendsPerHour=" + maxResendsPerHour + "]";
    }
}
