package com.farmcrop.auth.controller;

import com.farmcrop.auth.dto.AuthResponse;
import com.farmcrop.auth.dto.LoginRequest;
import com.farmcrop.auth.dto.RegisterRequest;
import com.farmcrop.auth.service.AuthService;
import com.farmcrop.auth.service.RefreshTokenService;
import com.farmcrop.common.exception.ApiException;
import com.farmcrop.common.response.ApiResponse;
import com.farmcrop.security.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String REFRESH_COOKIE = "fc_refresh";
    static final String COOKIE_PATH = "/api/v1/auth";
    /** Cross-site HTML forms cannot set custom headers, and cross-origin JS needs a CORS preflight we only allow for our origins. */
    static final String CSRF_HEADER = "X-Requested-With";
    static final String CSRF_HEADER_VALUE = "XMLHttpRequest";

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final SecurityProperties properties;

    public AuthController(AuthService authService, RefreshTokenService refreshTokenService, SecurityProperties properties) {
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
        this.properties = properties;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthService.AuthResult result = authService.register(request);
        return withRefreshCookie(HttpStatus.CREATED, result, "Your FarmCrop account has been created.");
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        AuthService.AuthResult result = authService.login(request, http.getRemoteAddr());
        return withRefreshCookie(HttpStatus.OK, result, null);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            @RequestHeader(name = CSRF_HEADER, required = false) String csrfHeader) {
        requireCsrfHeader(csrfHeader);
        try {
            return withRefreshCookie(HttpStatus.OK, authService.refresh(refreshToken), null);
        } catch (ApiException ex) {
            if (ex.getStatus() != HttpStatus.UNAUTHORIZED || "REFRESH_RACE".equals(ex.getCode())) throw ex;
            // Dead session: clear the cookie so the browser stops presenting it.
            return ResponseEntity.status(ex.getStatus())
                    .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                    .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            @RequestHeader(name = CSRF_HEADER, required = false) String csrfHeader) {
        requireCsrfHeader(csrfHeader);
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                .body(ApiResponse.ok(null, "You have been signed out."));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(HttpStatus status, AuthService.AuthResult result, String message) {
        ResponseCookie cookie = refreshCookie(result.refreshToken(), refreshTokenService.ttl());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.ok(result.response(), message));
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie clearCookie() {
        return refreshCookie("", Duration.ZERO);
    }

    private static void requireCsrfHeader(String value) {
        if (!CSRF_HEADER_VALUE.equals(value)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CSRF_CHECK_FAILED", "This request was rejected for security reasons.");
        }
    }
}
