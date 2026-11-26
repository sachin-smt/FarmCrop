package com.farmcrop.security;

import com.farmcrop.user.entity.User;
import com.farmcrop.user.repository.UserRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.EnumSet;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties({SecurityProperties.class, com.farmcrop.auth.bootstrap.AdminBootstrapProperties.class})
public class JwtConfig {

    private static final int MIN_KEY_BYTES = 32;

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecretKey jwtSigningKey(SecurityProperties properties) {
        String secret = properties.jwt() == null ? null : properties.jwt().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not configured. Set it in the environment or backend/.env "
                    + "(base64, at least 32 bytes; e.g. generate with: openssl rand -base64 48).");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("JWT_SECRET must be base64-encoded.");
        }
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("JWT_SECRET must decode to at least " + MIN_KEY_BYTES + " bytes.");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.jwt().issuer())));
        return decoder;
    }

    /**
     * Turns a validated JWT into an authentication whose roles come from the database, rejecting
     * tokens of suspended/disabled/deleted accounts.
     */
    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(UserRepository userRepository) {
        return new DatabaseBackedJwtConverter(userRepository);
    }

    static class DatabaseBackedJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

        private final UserRepository userRepository;

        DatabaseBackedJwtConverter(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        // findWithRolesById fetch-joins roles, so no open transaction is needed here.
        // Eclipse JDT null analysis flags the Optional.filter(User::isActive) method reference (false positive).
        @SuppressWarnings("null")
        @Override
        public AbstractAuthenticationToken convert(Jwt jwt) {
            long userId;
            try {
                userId = Long.parseLong(jwt.getSubject());
            } catch (NumberFormatException ex) {
                throw new DisabledException("Invalid token subject.");
            }
            User user = userRepository.findWithRolesById(userId)
                    .filter(User::isActive)
                    .orElseThrow(() -> new DisabledException("Account is not active."));

            var roles = user.getRoles().stream()
                    .map(role -> role.getRoleName())
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(com.farmcrop.common.enums.UserRole.class)));
            var authorities = roles.stream().map(role -> new SimpleGrantedAuthority(role.authority())).toList();
            AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getUsername(), roles);
            return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
        }
    }
}
