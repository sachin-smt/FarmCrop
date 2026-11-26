package com.farmcrop.auth.dto;

import com.farmcrop.user.dto.UserProfileResponse;

import java.time.Instant;

/**
 * Returned by register/login/refresh. The access token is meant to be kept in memory by the client;
 * the refresh token is never in the body — it is set as an HttpOnly cookie.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        UserProfileResponse user
) {
}
