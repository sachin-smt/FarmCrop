package com.farmcrop.auth.service;

import com.farmcrop.auth.dto.AuthResponse;
import com.farmcrop.auth.dto.LoginRequest;
import com.farmcrop.auth.entity.RefreshToken.RevokeReason;
import com.farmcrop.auth.entity.PendingRegistration;
import com.farmcrop.auth.repository.PendingRegistrationRepository;
import com.farmcrop.common.exception.ApiException;
import com.farmcrop.security.JwtService;
import com.farmcrop.user.dto.UserProfileResponse;
import com.farmcrop.user.entity.User;
import com.farmcrop.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;

@Service
public class AuthService {

    /** Result of a successful authentication: response body plus the raw refresh token for the cookie. */
    public record AuthResult(AuthResponse response, String refreshToken) {
    }

    private final UserRepository userRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final Clock clock;
    /** Hash of a random secret, compared against when there is no pending sign-up (constant work per failure). */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PendingRegistrationRepository pendingRegistrationRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService,
                       RefreshTokenService refreshTokenService, LoginAttemptService loginAttemptService, Clock clock) {
        this.userRepository = userRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
        this.clock = clock;
        byte[] random = new byte[32];
        new java.security.SecureRandom().nextBytes(random);
        this.dummyPasswordHash = passwordEncoder.encode(java.util.Base64.getEncoder().encodeToString(random));
    }

    @Transactional
    public AuthResult login(LoginRequest request, String clientIp) {
        String identifier = request.usernameOrEmail().trim().toLowerCase(Locale.ROOT);
        loginAttemptService.assertAllowed(identifier, clientIp);

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(identifier, request.password()));
        } catch (AuthenticationException ex) {
            loginAttemptService.recordFailure(identifier, clientIp);
            // A sign-up that never verified its email is not an account. Say so, but only to someone who knows
            // the password chosen for it (everyone else gets the generic error).
            if (hasUnverifiedRegistration(identifier, request.password())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                        "Please verify your email address with the code we sent before logging in. "
                                + "To get a new code, submit the registration form again.");
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username/email or password.");
        }

        Long userId = Long.valueOf(authentication.getName());
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username/email or password."));
        // Status is revealed only to someone who proved knowledge of the password.
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED",
                    "This account is suspended. Please contact FarmCrop support.");
        }
        loginAttemptService.recordSuccess(identifier);
        user.setLastLoginAt(clock.instant());
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        User user = userRepository.findWithRolesById(rotation.userId()).orElse(null);
        if (user == null || !user.isActive()) {
            refreshTokenService.revokeAllForUser(rotation.userId(), RevokeReason.ACCOUNT_DISABLED);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_REVOKED", "Your session has ended. Please sign in again.");
        }
        JwtService.IssuedToken access = jwtService.issueAccessToken(user);
        return new AuthResult(toResponse(access, user), rotation.newRawToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeFamilyOf(rawRefreshToken);
    }

    /**
     * After a failed password check: is this a sign-up whose email was never verified? Only when no account exists
     * for the identifier (an account created meanwhile, e.g. by Google, must get the normal error), and only the
     * newest pending sign-up is considered. Exactly one hash comparison runs in every case (a dummy hash when there
     * is no candidate), so response time does not reveal whether a pending sign-up exists.
     */
    private boolean hasUnverifiedRegistration(String identifier, String password) {
        PendingRegistration candidate = null;
        if (userRepository.findForLogin(identifier).isEmpty()) {
            candidate = (identifier.contains("@")
                    ? pendingRegistrationRepository.findOpenByEmail(identifier)
                    : pendingRegistrationRepository.findFirstByUsernameAndConsumedAtIsNullOrderByUpdatedAtDesc(identifier))
                    .orElse(null);
        }
        boolean matches = passwordEncoder.matches(password, candidate != null ? candidate.getPasswordHash() : dummyPasswordHash);
        return candidate != null && matches;
    }

    /** Starts a new session (access token + refresh-token family). Shared with {@link FirebaseLoginService}. */
    AuthResult issueTokens(User user) {
        JwtService.IssuedToken access = jwtService.issueAccessToken(user);
        String refresh = refreshTokenService.issueNewFamily(user.getId());
        return new AuthResult(toResponse(access, user), refresh);
    }

    private static AuthResponse toResponse(JwtService.IssuedToken access, User user) {
        return new AuthResponse(access.value(), "Bearer", access.expiresAt(), UserProfileResponse.from(user));
    }
}
