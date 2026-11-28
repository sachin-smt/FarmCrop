package com.farmcrop.auth.firebase;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Firebase Authentication settings (Google and phone OTP sign-in).
 *
 * @param projectId  Firebase project id (not a secret). Blank disables Google/phone sign-in.
 * @param maxAuthAge how recently the user must have completed the Google/OTP step for a token to be accepted.
 * @param jwkSetUri  Google's public signing keys for Firebase ID tokens.
 */
@ConfigurationProperties(prefix = "farmcrop.firebase")
public record FirebaseProperties(String projectId, Duration maxAuthAge, String jwkSetUri) {

    public static final String DEFAULT_JWK_SET_URI =
            "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

    public FirebaseProperties {
        projectId = projectId == null ? "" : projectId.trim();
        if (maxAuthAge == null) maxAuthAge = Duration.ofMinutes(5);
        if (jwkSetUri == null || jwkSetUri.isBlank()) jwkSetUri = DEFAULT_JWK_SET_URI;
    }

    public boolean enabled() {
        return !projectId.isEmpty();
    }

    public String issuer() {
        return "https://securetoken.google.com/" + projectId;
    }
}
