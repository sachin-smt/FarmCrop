package com.farmcrop.auth.service;

import com.farmcrop.auth.dto.AuthResponse;
import com.farmcrop.auth.dto.LoginRequest;
import com.farmcrop.auth.dto.RegisterRequest;
import com.farmcrop.auth.entity.RefreshToken.RevokeReason;
import com.farmcrop.common.enums.UserRole;
import com.farmcrop.common.exception.ApiException;
import com.farmcrop.common.response.ApiResponse.FieldError;
import com.farmcrop.common.validation.ValidationPatterns;
import com.farmcrop.security.JwtService;
import com.farmcrop.user.dto.UserProfileResponse;
import com.farmcrop.user.entity.Role;
import com.farmcrop.user.entity.User;
import com.farmcrop.user.repository.RoleRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {

    /** Result of a successful authentication: response body plus the raw refresh token for the cookie. */
    public record AuthResult(AuthResponse response, String refreshToken) {
    }

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final Clock clock;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService,
                       RefreshTokenService refreshTokenService, LoginAttemptService loginAttemptService, Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginAttemptService = loginAttemptService;
        this.clock = clock;
    }

    @Transactional
    public AuthResult register(RegisterRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String phone = ValidationPatterns.normalizePhone(request.phone());

        List<FieldError> errors = new ArrayList<>();
        if (!username.matches(ValidationPatterns.USERNAME)) {
            errors.add(new FieldError("username", "Use 3-30 lower-case letters, numbers, dots or underscores."));
        } else if (ValidationPatterns.RESERVED_USERNAMES.contains(username)) {
            errors.add(new FieldError("username", "This username is not available."));
        }
        if (phone == null || !phone.matches(ValidationPatterns.PHONE)) {
            errors.add(new FieldError("phone", "Enter a valid phone number (10-15 digits)."));
        }
        if (!request.password().equals(request.confirmPassword())) {
            errors.add(new FieldError("confirmPassword", "Passwords do not match."));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Please correct the highlighted fields.", errors);
        }

        if (userRepository.existsByUsername(username)) errors.add(new FieldError("username", "This username is already taken."));
        if (userRepository.existsByEmail(email)) errors.add(new FieldError("email", "An account with this email already exists."));
        if (userRepository.existsByPhone(phone)) errors.add(new FieldError("phone", "An account with this phone number already exists."));
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS", "Some details are already registered.", errors);
        }

        User user = new User(request.fullName().trim(), username, email, phone, passwordEncoder.encode(request.password()));
        user.grantRole(requireRole(UserRole.BUYER));
        user.setLastLoginAt(clock.instant());
        userRepository.saveAndFlush(user);
        return issueTokens(user);
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

    private AuthResult issueTokens(User user) {
        JwtService.IssuedToken access = jwtService.issueAccessToken(user);
        String refresh = refreshTokenService.issueNewFamily(user.getId());
        return new AuthResult(toResponse(access, user), refresh);
    }

    private static AuthResponse toResponse(JwtService.IssuedToken access, User user) {
        return new AuthResponse(access.value(), "Bearer", access.expiresAt(), UserProfileResponse.from(user));
    }

    private Role requireRole(UserRole role) {
        return roleRepository.findByRoleName(role)
                .orElseThrow(() -> new IllegalStateException("Role " + role + " is missing; check Flyway migration V1."));
    }
}
