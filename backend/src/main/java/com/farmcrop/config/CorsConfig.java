package com.farmcrop.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * CORS for browser clients. Only explicitly configured origins are allowed; credentials are
 * permitted because the refresh token travels in an HttpOnly cookie.
 */
@Configuration
public class CorsConfig {

    // Eclipse JDT null analysis cannot see through unannotated Function<T, R> lambdas (false positive).
    @SuppressWarnings("null")
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${farmcrop.cors.allowed-origins}") String allowedOrigins) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(origin -> origin.trim())
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (origins.contains("*")) {
            throw new IllegalStateException("Wildcard CORS origins are not allowed with credentialed requests.");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
