package com.farmcrop.auth;

import com.farmcrop.auth.firebase.FirebaseProperties;
import com.farmcrop.auth.firebase.FirebaseTokenVerifier;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mints Firebase-shaped ID tokens signed with a throw-away RSA key generated per test run, and wires the
 * production {@link FirebaseTokenVerifier} (same validators) to trust that key instead of Google's.
 * This exercises real signature/claim verification without any network access or real credentials.
 */
public final class FirebaseTestTokens {

    public static final String PROJECT_ID = "farmcrop-test";
    private static final RSAKey TRUSTED_KEY = generate("trusted");
    private static final RSAKey UNTRUSTED_KEY = generate("untrusted");

    private FirebaseTestTokens() {
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        FirebaseTokenVerifier testFirebaseTokenVerifier(FirebaseProperties properties, Clock clock) throws JOSEException {
            return new FirebaseTokenVerifier(properties,
                    NimbusJwtDecoder.withPublicKey(TRUSTED_KEY.toRSAPublicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build(),
                    clock);
        }
    }

    /** Fluent token description; defaults describe a valid, fresh token for {@link #PROJECT_ID}. */
    public static final class Token {
        private final Map<String, Object> claims = new HashMap<>();
        private final Map<String, Object> firebase = new HashMap<>();
        private String issuer = "https://securetoken.google.com/" + PROJECT_ID;
        private List<String> audience = List.of(PROJECT_ID);
        private Instant authTime = Instant.now().minusSeconds(5);
        private Instant expiresAt = Instant.now().plus(Duration.ofHours(1));
        private String subject;
        private RSAKey key = TRUSTED_KEY;

        private Token(String uid) {
            this.subject = uid;
        }

        public Token issuer(String value) { this.issuer = value; return this; }
        public Token audience(String value) { this.audience = List.of(value); return this; }
        public Token authTime(Instant value) { this.authTime = value; return this; }
        public Token expiresAt(Instant value) { this.expiresAt = value; return this; }
        public Token subject(String value) { this.subject = value; return this; }
        public Token untrustedKey() { this.key = UNTRUSTED_KEY; return this; }
        public Token claim(String name, Object value) { claims.put(name, value); return this; }

        public String sign() {
            Instant issuedAt = expiresAt.minus(Duration.ofHours(1));
            JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                    .issuer(issuer)
                    .audience(audience)
                    .subject(subject)
                    .issuedAt(issuedAt.isAfter(Instant.now()) ? Instant.now() : issuedAt)
                    .expiresAt(expiresAt)
                    .claim("auth_time", authTime.getEpochSecond())
                    .claim("user_id", subject)
                    .claim("firebase", firebase)
                    // Real Firebase tokens differ per sign-in; RS256 is deterministic, so make each test token unique.
                    .claim("test_nonce", java.util.UUID.randomUUID().toString());
            claims.forEach(builder::claim);
            NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
            JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).type("JWT").build();
            return encoder.encode(JwtEncoderParameters.from(header, builder.build())).getTokenValue();
        }
    }

    /** A Google sign-in token for the given Google account id and email. */
    public static Token google(String googleSub, String email, boolean emailVerified) {
        Token token = new Token("fb-g-" + googleSub);
        token.firebase.put("sign_in_provider", "google.com");
        token.firebase.put("identities", Map.of("google.com", List.of(googleSub), "email", List.of(email)));
        return token.claim("email", email).claim("email_verified", emailVerified).claim("name", "Asha Kumari");
    }

    /** A phone (SMS OTP) sign-in token for the given E.164 number. */
    public static Token phone(String e164) {
        Token token = new Token("fb-p-" + e164.replace("+", ""));
        token.firebase.put("sign_in_provider", "phone");
        token.firebase.put("identities", Map.of("phone", List.of(e164)));
        return token.claim("phone_number", e164);
    }

    private static RSAKey generate(String kid) {
        try {
            return new RSAKeyGenerator(2048).keyID(kid).generate();
        } catch (JOSEException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
