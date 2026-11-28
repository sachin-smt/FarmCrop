package com.farmcrop.auth.firebase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Clock;

/**
 * Wires the Firebase ID-token verifier. Deliberately not exposed as a {@code JwtDecoder} bean: the API's own
 * resource-server decoder (HS256 FarmCrop access tokens, see JwtConfig) must stay the only one.
 */
@Configuration
@EnableConfigurationProperties(FirebaseProperties.class)
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    FirebaseTokenVerifier firebaseTokenVerifier(FirebaseProperties properties, Clock clock) {
        if (!properties.enabled()) {
            log.info("Google and phone sign-in are disabled: FIREBASE_PROJECT_ID is not set.");
            return FirebaseTokenVerifier.disabled(properties, clock);
        }
        // Keys are fetched lazily and cached by Nimbus; nothing is contacted at startup.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        return new FirebaseTokenVerifier(properties, decoder, clock);
    }
}
