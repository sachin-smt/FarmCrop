package com.farmcrop.auth.service;

import com.farmcrop.auth.dto.PendingRegistrationResponse;
import com.farmcrop.auth.dto.RegisterRequest;
import com.farmcrop.auth.dto.VerifyRegistrationRequest;
import com.farmcrop.auth.entity.PendingRegistration;
import com.farmcrop.auth.otp.EmailOtpCodec;
import com.farmcrop.auth.otp.EmailOtpProperties;
import com.farmcrop.auth.repository.PendingRegistrationRepository;
import com.farmcrop.common.enums.UserRole;
import com.farmcrop.common.exception.ApiException;
import com.farmcrop.common.ratelimit.RateLimiter;
import com.farmcrop.common.response.ApiResponse.FieldError;
import com.farmcrop.common.validation.ValidationPatterns;
import com.farmcrop.notification.email.EmailDeliveryException;
import com.farmcrop.notification.email.EmailMessage;
import com.farmcrop.notification.email.EmailSender;
import com.farmcrop.user.entity.Role;
import com.farmcrop.user.entity.User;
import com.farmcrop.user.repository.RoleRepository;
import com.farmcrop.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Email + password registration with a six-digit email OTP.
 *
 * <ol>
 *   <li>{@link #register}: validates, stores a {@link PendingRegistration} (BCrypt password hash, HMAC of the code)
 *       and emails the code. No account and no session exist yet.</li>
 *   <li>{@link #verify}: email + code + the same password; under a row lock it checks expiry and attempts,
 *       consumes the code, creates the ACTIVE BUYER account (email verified) and starts the normal session.</li>
 *   <li>{@link #resend}: a new code (old one stops working), subject to cooldown and an hourly limit.</li>
 * </ol>
 * Existing accounts, password login, refresh, Google and phone sign-in are unaffected: unverified
 * registrations never exist in the users table.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);
    private static final Duration RESEND_WINDOW = Duration.ofHours(1);
    private static final Duration IP_WINDOW = Duration.ofHours(1);
    private static final int REGISTRATIONS_PER_IP_PER_HOUR = 10;
    private static final int RESENDS_PER_IP_PER_HOUR = 10;
    private static final int VERIFICATIONS_PER_IP_PER_15_MIN = 30;

    private final PendingRegistrationRepository pendingRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailOtpCodec otpCodec;
    private final EmailOtpProperties otpProperties;
    private final EmailSender emailSender;
    private final RateLimiter rateLimiter;
    private final AuthService authService;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final TransactionTemplate readOnlyTransactions;

    public RegistrationService(PendingRegistrationRepository pendingRepository, UserRepository userRepository,
                               RoleRepository roleRepository, PasswordEncoder passwordEncoder, EmailOtpCodec otpCodec,
                               EmailOtpProperties otpProperties, EmailSender emailSender, RateLimiter rateLimiter,
                               AuthService authService, Clock clock, PlatformTransactionManager transactionManager) {
        this.pendingRepository = pendingRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpCodec = otpCodec;
        this.otpProperties = otpProperties;
        this.emailSender = emailSender;
        this.rateLimiter = rateLimiter;
        this.authService = authService;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
        this.readOnlyTransactions = new TransactionTemplate(transactionManager);
        this.readOnlyTransactions.setReadOnly(true);
    }

    public PendingRegistrationResponse register(RegisterRequest request, String clientIp) {
        rateLimiter.acquire("register:" + clientIp, REGISTRATIONS_PER_IP_PER_HOUR, IP_WINDOW);
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = normalizeEmail(request.email());
        String phone = ValidationPatterns.normalizePhone(request.phone());

        List<FieldError> errors = new ArrayList<>();
        ApiException usernameInvalid = usernameProblem(username);
        if (usernameInvalid != null) errors.addAll(usernameInvalid.getFieldErrors());
        if (phone == null || !phone.matches(ValidationPatterns.PHONE)) {
            errors.add(new FieldError("phone", "Enter a valid phone number (10-15 digits)."));
        }
        if (!request.password().equals(request.confirmPassword())) {
            errors.add(new FieldError("confirmPassword", "Passwords do not match."));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Please correct the highlighted fields.", errors);
        }
        assertDeliveryAvailable();
        String passwordHash = passwordEncoder.encode(request.password()); // slow: done before taking any row lock

        // Usernames are public handles, so a taken one is reported (the user must choose another).
        // Whether the EMAIL or PHONE belongs to an account is not revealed here: an existing account's email gets
        // the same 202 (and its owner a notice); a taken phone is reported only at verification, i.e. only to
        // someone who proved they control an inbox.
        if (userRepository.existsByUsername(username)) {
            throw accountExists(List.of(new FieldError("username", "This username is already taken.")));
        }

        // 1) Commit the pending registration and its new code (short transaction, row lock released at commit).
        IssuedCode issued = transactions.execute(status -> {
            Instant now = clock.instant();
            if (userRepository.existsByEmail(email)) return null; // existing account: no pending row, see below
            PendingRegistration pending = pendingRepository.findByEmailForUpdate(email).orElse(null);
            boolean resend = pending != null;
            if (pending == null) {
                assertMaySendToUnknown(email); // same limits as a pending registration
                pending = new PendingRegistration(email, now);
            } else {
                // Same email again (e.g. the user lost the code or corrected a detail): reuse the row, never a second one.
                assertMaySendAgain(pending, now);
                pending.recordResend(now, RESEND_WINDOW);
            }
            pending.setDetails(request.fullName().trim(), username, phone, passwordHash);
            return issueCode(pending, resend, now);
        });
        if (issued == null) return notifyExistingAccount(email);
        // 2) Send only after the commit: no database lock or pooled connection is held while talking to SMTP.
        return deliverOrRelease(issued);
    }

    public PendingRegistrationResponse resend(String rawEmail, String clientIp) {
        rateLimiter.acquire("register-resend:" + clientIp, RESENDS_PER_IP_PER_HOUR, IP_WINDOW);
        assertDeliveryAvailable();
        String email = normalizeEmail(rawEmail);
        IssuedCode issued = transactions.execute(status -> {
            Instant now = clock.instant();
            PendingRegistration pending = pendingRepository.findByEmailForUpdate(email)
                    .filter(found -> !found.isConsumed())
                    .orElse(null);
            if (pending == null) {
                // Unknown or already-completed: same limits and same answer as a real resend, but nothing is sent.
                assertMaySendToUnknown(email);
                return null;
            }
            assertMaySendAgain(pending, now);
            pending.recordResend(now, RESEND_WINDOW);
            return issueCode(pending, true, now);
        });
        return issued == null ? genericResponse(email) : deliverOrRelease(issued);
    }

    /**
     * Someone submitted the registration form with an email that already has an account. They get the normal
     * "code sent" answer; the account owner gets a notice instead of a code (nothing can be verified).
     */
    private PendingRegistrationResponse notifyExistingAccount(String email) {
        assertMaySendToUnknown(email);
        String text = """
                Hello,

                Someone tried to create a new FarmCrop account with this email address, but it already has an account.

                If this was you, simply log in (or use "Continue with Google" if that is how you signed up).
                If it was not you, you can ignore this email; your account has not been changed.

                — FarmCrop
                """;
        String html = """
                <div style="font-family:Arial,sans-serif;color:#122018;max-width:480px">
                  <h2 style="color:#064e2a;margin:0 0 12px">You already have a FarmCrop account</h2>
                  <p>Someone tried to create a new FarmCrop account with this email address, but it already has an account.</p>
                  <p>If this was you, simply log in (or use “Continue with Google” if that is how you signed up).
                     If it was not you, you can ignore this email; your account has not been changed.</p>
                  <p style="color:#66736b">— FarmCrop</p>
                </div>
                """;
        try {
            emailSender.send(new EmailMessage(email, "Your FarmCrop account", text, html));
        } catch (EmailDeliveryException ex) {
            throw deliveryFailed();
        }
        return genericResponse(email);
    }

    /** Indistinguishable from a real "code sent" answer. */
    private PendingRegistrationResponse genericResponse(String email) {
        Instant now = clock.instant();
        return new PendingRegistrationResponse(email, true, now.plus(otpProperties.ttl()), now.plus(otpProperties.resendCooldown()));
    }

    /** Applies the per-email cooldown and hourly limit to emails without a pending registration. */
    private void assertMaySendToUnknown(String email) {
        rateLimiter.tryAcquire("email-send-cooldown:" + email, 1, otpProperties.resendCooldown())
                .ifPresent(wait -> { throw cooldown(wait); });
        rateLimiter.tryAcquire("email-send-hour:" + email, otpProperties.maxResendsPerHour() + 1, RESEND_WINDOW)
                .ifPresent(wait -> { throw resendLimit(); });
    }

    /** A committed, not yet delivered code. Holds the plain code only in memory until the email is sent. */
    private record IssuedCode(Long pendingId, String email, String fullName, String code, boolean resend,
                              PendingRegistrationResponse response) {
        @Override
        public String toString() {
            return "IssuedCode[pendingId=" + pendingId + ", code=***]";
        }
    }

    private IssuedCode issueCode(PendingRegistration pending, boolean resend, Instant now) {
        String code = otpCodec.generate();
        pending.issueOtp(otpCodec.hmac(pending.getEmail(), code), now.plus(otpProperties.ttl()), now);
        pendingRepository.saveAndFlush(pending);
        return new IssuedCode(pending.getId(), pending.getEmail(), pending.getFullName(), code, resend, toResponse(pending));
    }

    /**
     * Sends the committed code. If the provider fails, the cooldown and resend count consumed by this attempt are
     * given back (the undelivered code is useless to anyone), so the user can retry immediately.
     */
    private PendingRegistrationResponse deliverOrRelease(IssuedCode issued) {
        if (issued == null) throw new IllegalStateException("No code was issued");
        try {
            deliver(issued.email(), issued.fullName(), issued.code());
            return issued.response();
        } catch (ApiException deliveryFailed) {
            transactions.executeWithoutResult(status -> pendingRepository.findById(issued.pendingId())
                    .ifPresent(pending -> {
                        pending.releaseFailedDelivery(otpProperties.resendCooldown(), issued.resend());
                        pendingRepository.saveAndFlush(pending);
                    }));
            throw deliveryFailed;
        }
    }

    /** What one verification attempt decided; failures carry state changes (attempt counts) that must be kept. */
    private record VerifyOutcome(AuthService.AuthResult result, ApiException failure) {
        static VerifyOutcome ok(AuthService.AuthResult result) { return new VerifyOutcome(result, null); }
        static VerifyOutcome fail(ApiException failure) { return new VerifyOutcome(null, failure); }
    }

    /**
     * Verifies the code and creates the account. Runs in an explicit transaction so that:
     * <ul>
     *   <li>expected failures (wrong code, ...) are returned, not thrown, and their attempt counts are committed;</li>
     *   <li>a unique-constraint race at account creation (two sign-ups verifying the same username at the same
     *       moment) rolls back everything, keeping the pending registration and its code intact, and is then
     *       reported as a normal field error instead of a generic conflict.</li>
     * </ul>
     * The pending row is locked for the whole transaction, so a code can be consumed only once.
     */
    public AuthService.AuthResult verify(VerifyRegistrationRequest request, String clientIp) {
        rateLimiter.acquire("register-verify:" + clientIp, VERIFICATIONS_PER_IP_PER_15_MIN, Duration.ofMinutes(15));
        if (!otpCodec.isConfigured()) throw unavailable();
        String email = normalizeEmail(request.email());
        String newUsername = request.username() == null || request.username().isBlank()
                ? null : request.username().trim().toLowerCase(Locale.ROOT);

        VerifyOutcome outcome;
        try {
            outcome = transactions.execute(status -> verifyInTransaction(email, request, newUsername));
        } catch (DataIntegrityViolationException ex) {
            // Lost a race with another account creation; nothing was written. Explain which detail is taken.
            throw readOnlyTransactions.execute(status -> conflictFor(email));
        }
        if (outcome == null) throw new IllegalStateException("Verification produced no outcome");
        if (outcome.failure() != null) throw outcome.failure();
        return outcome.result();
    }

    private VerifyOutcome verifyInTransaction(String email, VerifyRegistrationRequest request, String newUsername) {
        Instant now = clock.instant();
        PendingRegistration pending = pendingRepository.findByEmailForUpdate(email).orElse(null);
        // Unknown, completed, expired and wrong codes all get the same answer (and the same attempt limit), so
        // this endpoint does not reveal which emails have a registration or an account.
        if (pending == null || pending.isConsumed()) {
            int failures = rateLimiter.hit("verify-failures:" + email, otpProperties.ttl());
            return VerifyOutcome.fail(failures >= otpProperties.maxAttempts() ? tooManyAttempts() : invalidCode());
        }
        if (pending.getFailedAttempts() >= otpProperties.maxAttempts()) return VerifyOutcome.fail(tooManyAttempts());
        if (!now.isBefore(pending.getOtpExpiresAt()) || !otpCodec.matches(email, request.otp(), pending.getOtpHmac())) {
            pending.recordFailedAttempt(now);
            pendingRepository.saveAndFlush(pending);
            return VerifyOutcome.fail(pending.getFailedAttempts() >= otpProperties.maxAttempts() ? tooManyAttempts() : invalidCode());
        }
        // Correct code: it must also be the registration this password was chosen for (prevents someone else's
        // earlier sign-up for this email from being activated with their password).
        if (!passwordEncoder.matches(request.password(), pending.getPasswordHash())) {
            pending.recordFailedAttempt(now);
            pendingRepository.saveAndFlush(pending);
            return VerifyOutcome.fail(new ApiException(HttpStatus.BAD_REQUEST, "REGISTRATION_PASSWORD_MISMATCH",
                    "This code belongs to a registration with a different password. Please register again."));
        }

        // Proven owner of the code and password: they may pick another username here (e.g. after a conflict)
        // without needing a new code. Not an attempt failure if invalid.
        if (newUsername != null && !newUsername.equals(pending.getUsername())) {
            ApiException invalid = usernameProblem(newUsername);
            if (invalid != null) return VerifyOutcome.fail(invalid);
            pending.changeUsername(newUsername, now);
        }

        // Details may have been taken meanwhile (another sign-up verified first, Google sign-in, profile change).
        List<FieldError> conflicts = conflicts(pending.getUsername(), email, pending.getPhone());
        if (!conflicts.isEmpty()) {
            pendingRepository.saveAndFlush(pending);
            return VerifyOutcome.fail(accountExists(conflicts));
        }
        pending.consume(now);
        pendingRepository.saveAndFlush(pending);

        User user = new User(pending.getFullName(), pending.getUsername(), email, pending.getPhone(), pending.getPasswordHash());
        user.grantRole(requireRole(UserRole.BUYER));
        user.setEmailVerified(true);
        user.setLastLoginAt(now);
        userRepository.saveAndFlush(user); // unique constraints are the final guard against a concurrent duplicate
        log.info("Email registration verified; created buyer {}", user.getId());
        return VerifyOutcome.ok(authService.issueTokens(user));
    }

    /** After a lost unique-constraint race: which of this registration's details now belong to an account. */
    private ApiException conflictFor(String email) {
        PendingRegistration pending = pendingRepository.findOpenByEmail(email).orElse(null);
        List<FieldError> conflicts = pending == null ? List.of() : conflicts(pending.getUsername(), email, pending.getPhone());
        if (conflicts.isEmpty()) {
            return new ApiException(HttpStatus.CONFLICT, "REGISTRATION_CONFLICT",
                    "Your account could not be created just now. Please try again.");
        }
        return accountExists(conflicts);
    }

    /** Housekeeping: drop consumed registrations after a day and abandoned ones a week after their code expired. */
    @Scheduled(cron = "0 37 3 * * *")
    @Transactional
    public void purge() {
        Instant now = clock.instant();
        int deleted = pendingRepository.purge(now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(7)));
        if (deleted > 0) log.info("Purged {} old pending registrations", deleted);
    }

    private List<FieldError> conflicts(String username, String email, String phone) {
        List<FieldError> errors = new ArrayList<>();
        if (userRepository.existsByUsername(username)) errors.add(new FieldError("username", "This username is already taken."));
        if (userRepository.existsByEmail(email)) errors.add(new FieldError("email", "An account with this email already exists."));
        if (userRepository.existsByPhoneIn(ValidationPatterns.equivalentPhones(phone))) {
            errors.add(new FieldError("phone", "An account with this phone number already exists."));
        }
        return errors;
    }

    private static ApiException accountExists(List<FieldError> errors) {
        return new ApiException(HttpStatus.CONFLICT, "ACCOUNT_EXISTS", "Some details are already registered.", errors);
    }

    /** Same username rules as registration; null if acceptable. */
    private static ApiException usernameProblem(String username) {
        String message = !username.matches(ValidationPatterns.USERNAME)
                ? "Use 3-30 lower-case letters, numbers, dots or underscores."
                : ValidationPatterns.RESERVED_USERNAMES.contains(username) ? "This username is not available." : null;
        return message == null ? null : new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Please correct the highlighted fields.", List.of(new FieldError("username", message)));
    }

    private void assertMaySendAgain(PendingRegistration pending, Instant now) {
        Instant availableAt = pending.getLastSentAt().plus(otpProperties.resendCooldown());
        if (now.isBefore(availableAt)) throw cooldown(Duration.between(now, availableAt));
        if (pending.resendsInWindow(now, RESEND_WINDOW) >= otpProperties.maxResendsPerHour()) throw resendLimit();
    }

    private static ApiException cooldown(Duration wait) {
        long seconds = Math.max(1, (wait.toMillis() + 999) / 1000);
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RESEND_COOLDOWN",
                "Please wait " + seconds + " seconds before requesting a new code.");
    }

    private static ApiException resendLimit() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_RESEND_LIMIT",
                "Too many codes requested for this email. Please try again in an hour.");
    }

    private static ApiException invalidCode() {
        return new ApiException(HttpStatus.BAD_REQUEST, "OTP_INVALID",
                "That code is incorrect or has expired. Check the latest email or request a new code.");
    }

    private static ApiException deliveryFailed() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_DELIVERY_FAILED",
                "We could not send the verification email. Please try again shortly.");
    }

    private void assertDeliveryAvailable() {
        if (!emailSender.isConfigured() || !otpCodec.isConfigured()) throw unavailable();
    }

    private void deliver(String toEmail, String fullName, String code) {
        long minutes = otpProperties.ttl().toMinutes();
        String text = """
                Hello %s,

                Your FarmCrop verification code is: %s

                It expires in %d minutes. If you did not create a FarmCrop account, you can ignore this email.

                — FarmCrop
                """.formatted(fullName, code, minutes);
        String html = """
                <div style="font-family:Arial,sans-serif;color:#122018;max-width:480px">
                  <h2 style="color:#064e2a;margin:0 0 12px">Verify your FarmCrop account</h2>
                  <p>Hello %s,</p>
                  <p>Your verification code is:</p>
                  <p style="font-size:28px;font-weight:bold;letter-spacing:6px;color:#0b6b3a;margin:8px 0 16px">%s</p>
                  <p>It expires in %d minutes. If you did not create a FarmCrop account, you can ignore this email.</p>
                  <p style="color:#66736b">— FarmCrop</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(fullName), code, minutes);
        try {
            emailSender.send(new EmailMessage(toEmail, "Verify your FarmCrop account", text, html));
        } catch (EmailDeliveryException ex) {
            throw deliveryFailed();
        }
    }

    private PendingRegistrationResponse toResponse(PendingRegistration pending) {
        return new PendingRegistrationResponse(pending.getEmail(), true, pending.getOtpExpiresAt(),
                pending.getLastSentAt().plus(otpProperties.resendCooldown()));
    }

    private Role requireRole(UserRole role) {
        return roleRepository.findByRoleName(role)
                .orElseThrow(() -> new IllegalStateException("Role " + role + " is missing; check Flyway migration V1."));
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static ApiException tooManyAttempts() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_ATTEMPTS_EXCEEDED",
                "Too many incorrect codes. Please request a new code.");
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_REGISTRATION_UNAVAILABLE",
                "Email registration is not available right now. Please try again later.");
    }
}
