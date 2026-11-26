package com.farmcrop.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.time.Instant;

/**
 * Stateless API security. Everything under /api/v1 requires authentication unless it is
 * explicitly listed as public. JWT authentication is added in the authentication milestone.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    static final String[] PUBLIC_ENDPOINTS = {
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/api/v1/public/**",
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Bearer tokens are not sent automatically by browsers, so CSRF does not apply to them.
                // Cookie-based endpoints (refresh) add their own protection in the auth milestone.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required."))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.")));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // Delegating encoder: BCrypt by default, with algorithm prefixes so hashes can be upgraded later.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private static void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Fixed, server-controlled strings only, so manual JSON construction is safe here.
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\",\"code\":\"" + code
                + "\",\"timestamp\":\"" + Instant.now() + "\"}");
    }
}
