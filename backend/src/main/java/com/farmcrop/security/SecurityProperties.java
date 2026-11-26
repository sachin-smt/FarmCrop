package com.farmcrop.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "farmcrop.security")
public record SecurityProperties(
        Jwt jwt,
        Duration refreshTokenTtl,
        boolean cookieSecure,
        int loginMaxFailures,
        Duration loginFailureWindow
) {

    public record Jwt(String secret, String issuer, Duration accessTokenTtl) {
    }
}
