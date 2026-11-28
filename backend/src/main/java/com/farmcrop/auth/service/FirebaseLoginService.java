package com.farmcrop.auth.service;

import com.farmcrop.auth.entity.ConsumedIdentityToken;
import com.farmcrop.auth.entity.UserAuthIdentity;
import com.farmcrop.auth.entity.UserAuthIdentity.Provider;
import com.farmcrop.auth.firebase.FirebaseTokenVerifier;
import com.farmcrop.auth.firebase.FirebaseTokenVerifier.FirebaseIdentity;
import com.farmcrop.auth.repository.ConsumedIdentityTokenRepository;
import com.farmcrop.auth.repository.UserAuthIdentityRepository;
import com.farmcrop.common.enums.UserRole;
import com.farmcrop.common.exception.ApiException;
import com.farmcrop.common.exception.ResourceNotFoundException;
import com.farmcrop.common.validation.ValidationPatterns;
import com.farmcrop.user.dto.UserProfileResponse;
import com.farmcrop.user.entity.Role;
import com.farmcrop.user.entity.User;
import com.farmcrop.user.repository.RoleRepository;
import com.farmcrop.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Google and phone (OTP) sign-in. The browser completes the Google popup or SMS code with Firebase
 * Authentication and sends us the resulting Firebase ID token; this service verifies it server-side and
 * then signs the user in to (or creates) their FarmCrop account, issuing the usual FarmCrop session.
 *
 * <p>Rules: every token is single-use and must be minutes old at most; first-time users become BUYERs only;
 * existing accounts are matched (Google account id, then Google-verified email; phone in any stored form) so no
 * duplicates are created; a phone number signs in to an existing account only if that account already verified
 * it; linking never adds or removes roles; ADMIN accounts can only use username/email + password.
 */
@Service
public class FirebaseLoginService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseLoginService.class);
    private static final String GOOGLE_PROVIDER = "google.com";
    private static final String PHONE_PROVIDER = "phone";
    private static final String USERNAME_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final FirebaseTokenVerifier verifier;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserAuthIdentityRepository identityRepository;
    private final ConsumedIdentityTokenRepository consumedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;
    private final AuthService authService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public FirebaseLoginService(FirebaseTokenVerifier verifier, UserRepository userRepository, RoleRepository roleRepository,
                                UserAuthIdentityRepository identityRepository,
                                ConsumedIdentityTokenRepository consumedTokenRepository, PasswordEncoder passwordEncoder,
                                LoginAttemptService loginAttemptService, AuthService authService, Clock clock) {
        this.verifier = verifier;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.identityRepository = identityRepository;
        this.consumedTokenRepository = consumedTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptService = loginAttemptService;
        this.authService = authService;
        this.clock = clock;
    }

    public boolean enabled() {
        return verifier.enabled();
    }

    @Transactional
    public AuthService.AuthResult signInWithGoogle(String idToken, String clientIp) {
        FirebaseIdentity identity = verifyAndConsume(idToken, GOOGLE_PROVIDER, clientIp);
        if (identity.googleSubject() == null) throw invalidFor(clientIp);
        Instant now = clock.instant();

        User user;
        UserAuthIdentity link = identityRepository.findByProviderAndProviderSubject(Provider.GOOGLE, identity.googleSubject())
                .orElse(null);
        if (link != null) {
            user = userRepository.findWithRolesById(link.getUserId()).orElseThrow(() -> invalidFor(clientIp));
        } else {
            String email = identity.email() == null ? null : identity.email().trim().toLowerCase(Locale.ROOT);
            if (email == null || !identity.emailVerified() || email.length() > 254) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "GOOGLE_EMAIL_NOT_VERIFIED",
                        "Your Google account does not have a verified email address.");
            }
            user = userRepository.findWithRolesByEmail(email).orElse(null);
            if (user != null) {
                if (identityRepository.existsByUserIdAndProvider(user.getId(), Provider.GOOGLE)) {
                    throw new ApiException(HttpStatus.CONFLICT, "GOOGLE_ACCOUNT_MISMATCH",
                            "This FarmCrop account is linked to a different Google account.");
                }
                assertMaySignIn(user);
                log.info("Linking Google sign-in to existing user {}", user.getId());
            } else {
                user = createBuyer(displayName(identity.name(), email), usernameBase(email), email, null);
                log.info("Created buyer {} from Google sign-in", user.getId());
            }
            link = identityRepository.save(new UserAuthIdentity(user.getId(), Provider.GOOGLE, identity.googleSubject(), now));
        }
        assertMaySignIn(user);
        link.setLastUsedAt(now);
        // Google vouched for this address (it is how the account was matched or created).
        if (user.getEmail() != null && user.getEmail().equalsIgnoreCase(identity.email()) && identity.emailVerified()) {
            user.setEmailVerified(true);
        }
        return complete(user, now, clientIp);
    }

    @Transactional
    public AuthService.AuthResult signInWithPhone(String idToken, String clientIp) {
        FirebaseIdentity identity = verifyAndConsume(idToken, PHONE_PROVIDER, clientIp);
        String phone = identity.phoneNumber();
        if (phone == null || !phone.matches(ValidationPatterns.INDIAN_MOBILE_E164)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PHONE_NUMBER",
                    "Only Indian mobile numbers (+91) can be used to sign in.");
        }
        Instant now = clock.instant();

        List<User> matches = userRepository.findWithRolesByPhoneIn(ValidationPatterns.equivalentPhones(phone));
        if (matches.size() > 1) {
            log.warn("Phone sign-in refused: number matches {} accounts", matches.size());
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_LINKED_TO_MULTIPLE_ACCOUNTS",
                    "This mobile number is linked to more than one account. Please sign in with your username or contact support.");
        }
        User user;
        if (matches.isEmpty()) {
            user = createBuyer("FarmCrop Buyer", "buyer", null, phone);
            log.info("Created buyer {} from phone sign-in", user.getId());
        } else {
            user = matches.getFirst();
            // Only a number this account already proved it owns may sign in to it. Otherwise whoever owns the
            // number (perhaps not the account holder, if it was mistyped at registration) would take the account
            // over. No new account is created either: that would duplicate the number.
            if (!user.isPhoneVerified()) {
                log.info("Phone sign-in refused for user {}: stored number was never verified", user.getId());
                throw new ApiException(HttpStatus.CONFLICT, "PHONE_NOT_VERIFIED_FOR_ACCOUNT",
                        "This mobile number belongs to an existing account that has not verified it yet. "
                                + "Please sign in with your username or email and password.");
            }
            assertMaySignIn(user);
        }
        return complete(user, now, clientIp);
    }

    /**
     * "Verify my phone" for a signed-in user: proves ownership of a number with a fresh Firebase phone token and
     * marks it verified on <em>this</em> account, which enables mobile OTP login for it. The number comes from the
     * verified token, never the request. A number that belongs to another account is refused, never moved.
     * Roles and all other profile data are left untouched.
     */
    @Transactional
    public UserProfileResponse verifyPhoneForUser(Long userId, String idToken, String clientIp) {
        FirebaseIdentity identity = verifyAndConsume(idToken, PHONE_PROVIDER, clientIp);
        String phone = identity.phoneNumber();
        if (phone == null || !phone.matches(ValidationPatterns.INDIAN_MOBILE_E164)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_PHONE_NUMBER",
                    "Only Indian mobile numbers (+91) can be verified.");
        }
        User user = userRepository.findWithRolesById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User account was not found."));

        List<String> equivalents = ValidationPatterns.equivalentPhones(phone);
        boolean usedElsewhere = userRepository.findWithRolesByPhoneIn(equivalents).stream()
                .anyMatch(other -> !other.getId().equals(userId));
        if (usedElsewhere) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_IN_USE",
                    "This mobile number is already used by another FarmCrop account.");
        }
        // Keep the stored form if it is the same number; otherwise switch the account to the verified number.
        if (user.getPhone() == null || !equivalents.contains(user.getPhone())) {
            user.setPhone(phone);
        }
        user.setPhoneVerified(true);
        loginAttemptService.recordSuccess("firebase-sign-in:" + clientIp);
        log.info("User {} verified their mobile number", userId);
        return UserProfileResponse.from(userRepository.saveAndFlush(user));
    }

    /** Housekeeping: forget consumed tokens once they could no longer be replayed anyway. */
    @Scheduled(cron = "0 27 3 * * *")
    @Transactional
    public void purgeConsumedTokens() {
        int deleted = consumedTokenRepository.deleteExpiredBefore(clock.instant().minus(Duration.ofHours(1)));
        if (deleted > 0) log.info("Purged {} consumed sign-in tokens", deleted);
    }

    private FirebaseIdentity verifyAndConsume(String idToken, String expectedProvider, String clientIp) {
        String throttleKey = "firebase-sign-in:" + clientIp;
        loginAttemptService.assertAllowed(throttleKey, clientIp);
        FirebaseIdentity identity;
        try {
            identity = verifier.verify(idToken);
        } catch (ApiException ex) {
            if (ex.getStatus() == HttpStatus.UNAUTHORIZED) loginAttemptService.recordFailure(throttleKey, clientIp);
            throw ex;
        }
        if (!expectedProvider.equals(identity.signInProvider())) throw invalidFor(clientIp);

        String hash = sha256(idToken);
        if (consumedTokenRepository.existsByTokenHash(hash)) {
            loginAttemptService.recordFailure(throttleKey, clientIp);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SIGN_IN_TOKEN_ALREADY_USED",
                    "This sign-in has already been used. Please sign in again.");
        }
        // A concurrent replay of the same token loses on the unique constraint (409 via the global handler).
        consumedTokenRepository.saveAndFlush(new ConsumedIdentityToken(hash, identity.expiresAt(), clock.instant()));
        return identity;
    }

    private AuthService.AuthResult complete(User user, Instant now, String clientIp) {
        loginAttemptService.recordSuccess("firebase-sign-in:" + clientIp);
        user.setLastLoginAt(now);
        userRepository.saveAndFlush(user);
        return authService.issueTokens(user);
    }

    /** Same order as password login: suspended users are told so only after proving who they are. */
    private static void assertMaySignIn(User user) {
        if (user.hasRole(UserRole.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_PASSWORD_LOGIN_REQUIRED",
                    "Admin accounts must sign in with their username or email and password.");
        }
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_SUSPENDED",
                    "This account is suspended. Please contact FarmCrop support.");
        }
    }

    private User createBuyer(String fullName, String usernameBase, String email, String phone) {
        // Google/phone users have no password; store an unguessable hash so password login can never match.
        byte[] secret = new byte[32];
        secureRandom.nextBytes(secret);
        String unusablePasswordHash = passwordEncoder.encode(Base64.getEncoder().encodeToString(secret));

        User user = new User(fullName, uniqueUsername(usernameBase), email, phone, unusablePasswordHash);
        user.setEmailVerified(email != null);
        user.setPhoneVerified(phone != null);
        user.grantRole(requireRole(UserRole.BUYER));
        return userRepository.saveAndFlush(user);
    }

    private String uniqueUsername(String base) {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder candidate = new StringBuilder(base).append('_');
            for (int i = 0; i < 6; i++) candidate.append(USERNAME_ALPHABET.charAt(secureRandom.nextInt(USERNAME_ALPHABET.length())));
            String username = candidate.toString();
            if (username.matches(ValidationPatterns.USERNAME) && !userRepository.existsByUsername(username)) return username;
        }
        throw new ApiException(HttpStatus.CONFLICT, "SIGN_IN_CONFLICT", "We could not create your account. Please try again.");
    }

    /** "asha.k+farm@gmail.com" -> "asha.k"; falls back to "buyer" when nothing usable remains. */
    static String usernameBase(String email) {
        String local = email.substring(0, email.indexOf('@') < 0 ? email.length() : email.indexOf('@'))
                .toLowerCase(Locale.ROOT).replaceAll("\\+.*$", "").replaceAll("[^a-z0-9._]", "");
        local = local.replaceAll("^[._]+", "").replaceAll("[._]+$", "");
        if (local.length() > 20) local = local.substring(0, 20).replaceAll("[._]+$", "");
        if (local.length() < 2 || ValidationPatterns.RESERVED_USERNAMES.contains(local)) return "buyer";
        return local;
    }

    private static String displayName(String name, String email) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) trimmed = email.substring(0, email.indexOf('@') < 0 ? email.length() : email.indexOf('@'));
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }

    private ApiException invalidFor(String clientIp) {
        loginAttemptService.recordFailure("firebase-sign-in:" + clientIp, clientIp);
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGN_IN_TOKEN", "We could not verify your sign-in. Please try again.");
    }

    private Role requireRole(UserRole role) {
        return roleRepository.findByRoleName(role)
                .orElseThrow(() -> new IllegalStateException("Role " + role + " is missing; check Flyway migration V1."));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
