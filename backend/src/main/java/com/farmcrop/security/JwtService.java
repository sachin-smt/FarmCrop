package com.farmcrop.security;

import com.farmcrop.user.entity.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Issues short-lived HS256 access tokens. Validation is done by Spring Security's JwtDecoder. */
@Service
public class JwtService {

    public record IssuedToken(String value, Instant expiresAt) {
    }

    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public JwtService(SecretKey jwtSigningKey, SecurityProperties properties, Clock clock) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
        this.properties = properties;
        this.clock = clock;
    }

    public IssuedToken issueAccessToken(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.jwt().accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("username", user.getUsername())
                // Informational for the client only; authorization always uses roles loaded from the database.
                .claim("roles", user.getRoles().stream().map(role -> role.getRoleName().name()).sorted().toList())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
