package com.farmcrop.auth.firebase;

import com.farmcrop.common.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Verifies Firebase Authentication ID tokens on the server, following Firebase's documented rules
 * (https://firebase.google.com/docs/auth/admin/verify-id-tokens#verify_id_tokens_using_a_third-party_jwt_library):
 * RS256 signature by Google's published keys, {@code iss = https://securetoken.google.com/<projectId>},
 * {@code aud = <projectId>}, unexpired, non-empty {@code sub}, and {@code auth_time} in the past. In addition,
 * FarmCrop requires {@code auth_time} to be recent, so every sign-in needs a freshly completed Google/OTP step.
 *
 * <p>No service-account credentials are needed: only public keys and the (public) project id.
 */
public class FirebaseTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(FirebaseTokenVerifier.class);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

    /** Claims FarmCrop uses from a verified token. Strings may be null when the provider did not supply them. */
    public record FirebaseIdentity(
            String uid,
            String signInProvider,
            String phoneNumber,
            String email,
            boolean emailVerified,
            String name,
            String googleSubject,
            Instant expiresAt
    ) {
    }

    private final FirebaseProperties properties;
    private final NimbusJwtDecoder decoder;
    private final Clock clock;

    /** @param decoder a decoder for Firebase's signing keys; this class installs the Firebase claim validators on it. */
    public FirebaseTokenVerifier(FirebaseProperties properties, NimbusJwtDecoder decoder, Clock clock) {
        this.properties = properties;
        this.decoder = decoder;
        this.clock = clock;
        if (decoder != null) decoder.setJwtValidator(validator(properties));
    }

    /** A verifier for when no Firebase project is configured: every sign-in attempt fails with 503. */
    public static FirebaseTokenVerifier disabled(FirebaseProperties properties, Clock clock) {
        return new FirebaseTokenVerifier(properties, null, clock);
    }

    public boolean enabled() {
        return decoder != null && properties.enabled();
    }

    public FirebaseIdentity verify(String idToken) {
        if (!enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SIGN_IN_METHOD_UNAVAILABLE",
                    "This sign-in method is not configured yet. Please use your username or email.");
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (BadJwtException ex) {
            log.debug("Rejected Firebase ID token: {}", ex.getMessage());
            throw invalidToken();
        } catch (JwtException ex) {
            // e.g. Google's key endpoint unreachable: not the user's fault.
            log.warn("Could not verify Firebase ID token: {}", ex.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SIGN_IN_VERIFICATION_UNAVAILABLE",
                    "We could not verify your sign-in right now. Please try again in a moment.");
        }

        Instant now = clock.instant();
        Instant authTime = epochSeconds(jwt.getClaim("auth_time"));
        if (authTime == null || authTime.isAfter(now.plus(CLOCK_SKEW))) throw invalidToken();
        if (authTime.isBefore(now.minus(properties.maxAuthAge()))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SIGN_IN_EXPIRED",
                    "Your verification has expired. Please sign in again.");
        }

        Map<String, Object> firebase = jwt.getClaimAsMap("firebase");
        String signInProvider = firebase == null ? null : asString(firebase.get("sign_in_provider"));
        String googleSubject = null;
        if (firebase != null && firebase.get("identities") instanceof Map<?, ?> identities
                && identities.get("google.com") instanceof List<?> ids && !ids.isEmpty()) {
            googleSubject = asString(ids.getFirst());
        }
        return new FirebaseIdentity(
                jwt.getSubject(),
                signInProvider,
                jwt.getClaimAsString("phone_number"),
                jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                jwt.getClaimAsString("name"),
                googleSubject,
                jwt.getExpiresAt());
    }

    static OAuth2TokenValidator<Jwt> validator(FirebaseProperties properties) {
        String projectId = properties.projectId();
        return new DelegatingOAuth2TokenValidator<>(
                // Signature is checked by the decoder; this adds exp/nbf (with skew) and the exact issuer.
                JwtValidators.createDefaultWithIssuer(properties.issuer()),
                new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.size() == 1 && aud.contains(projectId)),
                new JwtClaimValidator<String>(JwtClaimNames.SUB,
                        sub -> sub != null && !sub.isBlank() && sub.length() <= 128),
                new JwtClaimValidator<Instant>(JwtClaimNames.IAT, iat -> iat != null));
    }

    private static ApiException invalidToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGN_IN_TOKEN",
                "We could not verify your sign-in. Please try again.");
    }

    private static Instant epochSeconds(Object value) {
        if (value instanceof Number number) return Instant.ofEpochSecond(number.longValue());
        if (value instanceof Instant instant) return instant;
        return null;
    }

    private static String asString(Object value) {
        return value instanceof String string && !string.isBlank() ? string : null;
    }
}
