package com.farmcrop.auth.dto;

import java.time.Instant;

/**
 * Returned by register/resend: an email OTP was sent and must be verified before any session exists.
 * Never contains the code.
 */
public record PendingRegistrationResponse(
        String email,
        boolean verificationRequired,
        Instant expiresAt,
        Instant resendAvailableAt
) {
}
