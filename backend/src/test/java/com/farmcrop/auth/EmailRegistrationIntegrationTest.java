package com.farmcrop.auth;

import com.farmcrop.notification.email.EmailMessage;
import com.farmcrop.support.CapturingEmailSender;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static com.farmcrop.auth.AuthTestSupport.PASSWORD;
import static com.farmcrop.auth.AuthTestSupport.login;
import static com.farmcrop.auth.AuthTestSupport.registerJson;
import static com.farmcrop.auth.AuthTestSupport.verifyJson;
import static com.farmcrop.auth.AuthTestSupport.withUniqueIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Email + password registration with a six-digit email OTP (no real email is sent: see CapturingEmailSender). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailRegistrationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private record Signup(String username, String email, String phone) {
    }

    // ---------- registration & storage ----------

    @Test
    void registrationEmailsACodeStoresOnlyItsHmacAndCreatesNoAccountYet() throws Exception {
        Signup signup = newSignup();
        register(signup, PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.email").value(signup.email()))
                .andExpect(jsonPath("$.data.verificationRequired").value(true))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.resendAvailableAt").isNotEmpty())
                .andExpect(jsonPath("$.data.otp").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        String code = CapturingEmailSender.lastCodeFor(signup.email());
        assertThat(code).matches("\\d{6}");
        EmailMessage email = CapturingEmailSender.inbox(signup.email()).getLast();
        assertThat(email.subject()).isEqualTo("Verify your FarmCrop account");
        assertThat(email.text()).contains(code, "expires in 10 minutes");

        String storedHmac = jdbc.queryForObject("SELECT otp_hmac FROM pending_registrations WHERE email = ?", String.class, signup.email());
        String storedPassword = jdbc.queryForObject("SELECT password_hash FROM pending_registrations WHERE email = ?", String.class, signup.email());
        assertThat(storedHmac).matches("[0-9a-f]{64}").doesNotContain(code);
        assertThat(storedPassword).startsWith("{bcrypt}").doesNotContain(PASSWORD);
        assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", signup.email())).isZero();
    }

    @Test
    void codesAreKeyedHmacsNotPlainHashes() throws Exception {
        Signup signup = newSignup();
        register(signup, PASSWORD).andExpect(status().isAccepted());
        String code = CapturingEmailSender.lastCodeFor(signup.email());
        String storedHmac = jdbc.queryForObject("SELECT otp_hmac FROM pending_registrations WHERE email = ?", String.class, signup.email());
        // An unkeyed SHA-256 of the code (or of email:code) must not match what is stored.
        assertThat(storedHmac).isNotEqualTo(sha256(code)).isNotEqualTo(sha256(signup.email() + ":" + code));
    }

    @Test
    void repeatingARegistrationReusesThePendingRowAndInvalidatesTheOldCode() throws Exception {
        Signup signup = newSignup();
        register(signup, PASSWORD).andExpect(status().isAccepted());
        String first = CapturingEmailSender.lastCodeFor(signup.email());

        // Within the cooldown: refused, nothing changes.
        register(signup, PASSWORD).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OTP_RESEND_COOLDOWN"));

        expireCooldown(signup.email());
        register(signup, PASSWORD).andExpect(status().isAccepted());
        String second = CapturingEmailSender.lastCodeFor(signup.email());
        assertThat(count("SELECT COUNT(*) FROM pending_registrations WHERE email = ?", signup.email())).isEqualTo(1);

        if (!first.equals(second)) {
            verify(signup.email(), first, PASSWORD).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_INVALID"));
        }
        verify(signup.email(), second, PASSWORD).andExpect(status().isCreated());
        assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", signup.email())).isEqualTo(1);
    }

    @Test
    void anEmailThatAlreadyHasAnAccountGetsTheSameAnswerButOnlyANoticeIsSent() throws Exception {
        Signup existing = insertExistingAccount();
        int sentBefore = CapturingEmailSender.inbox(existing.email()).size();
        int usersBefore = count("SELECT COUNT(*) FROM users");
        Signup probe = new Signup("other" + AuthTestSupport.next(), existing.email().toUpperCase(), existing.phone());

        register(probe, PASSWORD)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.email").value(existing.email()))
                .andExpect(jsonPath("$.data.verificationRequired").value(true))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.resendAvailableAt").isNotEmpty())
                .andExpect(jsonPath("$.errors").doesNotExist());

        // The real owner is told; no code exists, nothing can be verified, nothing is created.
        List<EmailMessage> inbox = CapturingEmailSender.inbox(existing.email());
        assertThat(inbox).hasSize(sentBefore + 1);
        assertThat(inbox.getLast().subject()).isEqualTo("Your FarmCrop account");
        assertThat(inbox.getLast().text()).doesNotContainPattern("[0-9]{6}");
        assertThat(count("SELECT COUNT(*) FROM pending_registrations WHERE email = ?", existing.email())).isZero();
        verify(existing.email(), "123456", PASSWORD).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_INVALID"));
        assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(usersBefore);

        // Same cooldown as a real registration.
        register(probe, PASSWORD).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OTP_RESEND_COOLDOWN"));
    }

    @Test
    void aTakenUsernameIsTheOnlyDetailReportedAtRegistration() throws Exception {
        AuthTestSupport.Account existing = AuthTestSupport.register(mockMvc);
        mockMvc.perform(withUniqueIp(post("/api/v1/auth/register")).contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(existing.username().toUpperCase(), "fresh" + AuthTestSupport.next() + "@example.test",
                                "8" + String.format("%09d", AuthTestSupport.next()), PASSWORD, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_EXISTS"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("username"));
    }

    @Test
    void aPhoneThatBelongsToAnAccountIsReportedOnlyAfterTheEmailIsProven() throws Exception {
        AuthTestSupport.Account existing = AuthTestSupport.register(mockMvc);
        Signup signup = new Signup("phoneprobe" + AuthTestSupport.next(), "phoneprobe" + AuthTestSupport.next() + "@example.test",
                "+91" + existing.phone());
        register(signup, PASSWORD).andExpect(status().isAccepted());
        verify(signup.email(), CapturingEmailSender.lastCodeFor(signup.email()), PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_EXISTS"))
                .andExpect(jsonPath("$.errors[0].field").value("phone"));
        assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", signup.email())).isZero();
    }

    @Test
    void aFailedEmailDeliveryTellsTheUserAndAllowsAnImmediateRetry() throws Exception {
        Signup signup = newSignup();
        CapturingEmailSender.failNextSend();
        register(signup, PASSWORD)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMAIL_DELIVERY_FAILED"));
        assertThat(CapturingEmailSender.inbox(signup.email())).isEmpty();
        // No cooldown is charged for the failed send: retrying right away works and the code then verifies.
        register(signup, PASSWORD).andExpect(status().isAccepted());
        assertThat(count("SELECT COUNT(*) FROM pending_registrations WHERE email = ?", signup.email())).isEqualTo(1);

        expireCooldown(signup.email());
        CapturingEmailSender.failNextSend();
        resend(signup.email()).andExpect(status().isServiceUnavailable());
        resend(signup.email()).andExpect(status().isAccepted()); // immediately, and without using up a resend
        assertThat(count("SELECT resends_in_window FROM pending_registrations WHERE email = ?", signup.email())).isEqualTo(2);
        verify(signup.email(), CapturingEmailSender.lastCodeFor(signup.email()), PASSWORD).andExpect(status().isCreated());
    }

    // ---------- verification ----------

    @Test
    void theCorrectCodeCreatesAVerifiedBuyerAndStartsTheNormalSession() throws Exception {
        Signup signup = newSignup();
        register(signup, PASSWORD).andExpect(status().isAccepted());

        MvcResult result = verify(signup.email(), CapturingEmailSender.lastCodeFor(signup.email()), PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(signup.email()))
                .andExpect(jsonPath("$.data.user.emailVerified").value(true))
                .andExpect(jsonPath("$.data.user.roles").value(contains("BUYER")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("fc_refresh=")))
                .andReturn();

        // The refresh cookie works like any other session; then normal password login works too.
        mockMvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("fc_refresh", AuthTestSupport.refreshCookie(result))))
                .andExpect(status().isOk());
        assertThat(login(mockMvc, signup.email(), PASSWORD).getResponse().getStatus()).isEqualTo(200);
        assertThat(login(mockMvc, signup.username(), PASSWORD).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForList("SELECT r.role_name FROM user_roles ur JOIN roles r ON r.id = ur.role_id JOIN users u ON u.id = ur.user_id WHERE u.email = ?",
                String.class, signup.email())).containsExactly("BUYER");
    }

    @Test
    void anIncorrectCodeIsCountedAndRejected() throws Exception {
        Signup signup = newSignup();
        register(signup, PASSWORD).andExpect(status().isAccepted());
        String wrong = wrongCode(CapturingEmailSender.lastCodeFor(signup.email()));

        verify(signup.email(), wrong, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(count("SELECT failed_attempts FROM pending_registrations WHERE email = ?", signup.email())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", signup.email())).isZero();
    }

    @Test
    void anExpiredCodeIsRejected() throws Exception {
        Signup signup = newSignup();
        register(signup, PASSWORD).andExpect(status().isAccepted());
        // Timestamps are stored in UTC; shift the stored expiry (10 min ahead) to 1 minute in the past.
        jdbc.update("UPDATE pending_registrations SET otp_expires_at = TIMESTAMPADD(MINUTE, -11, otp_expires_at) WHERE email = ?", signup.email());

        verify(signup.email(), CapturingEmailSender.lastCodeFor(signup.email()), PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID")); // same answer as a wrong code
        assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", signup.email())).isZero();
    }

    @Test
    void aCodeCanBeUsedOnlyOnce() throws Exception {
        Signup signup = newSignup();
        register(signup, PASSWORD).andExpect(status().isAccepted());
        String code = CapturingEmailSender.lastCodeFor(signup.email());

        verify(signup.email(), code, PASSWORD).andExpect(status().isCreated());
        verify(signup.email(), code, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", signup.email())).isEqualTo(1);
    }

    @Test
    void fiveWrongCodesLockTheCodeEvenForTheRightValue() throws Exception {
        Signup signup = newSignup();
        register(signup, PASSWORD).andExpect(status().isAccepted());
        String code = CapturingEmailSender.lastCodeFor(signup.email());
        String wrong = wrongCode(code);

        for (int i = 1; i <= 4; i++) {
            verify(signup.email(), wrong, PASSWORD).andExpect(status().isBadRequest());
        }
        verify(signup.email(), wrong, PASSWORD).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
        verify(signup.email(), code, PASSWORD).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
        assertThat(count("SELECT failed_attempts FROM pending_registrations WHERE email = ?", signup.email())).isEqualTo(5);

        // A new code restarts the counter.
        expireCooldown(signup.email());
        resend(signup.email()).andExpect(status().isAccepted());
        verify(signup.email(), CapturingEmailSender.lastCodeFor(signup.email()), PASSWORD).andExpect(status().isCreated());
    }

    @Test
    void someoneElsesEarlierSignupForTheEmailCannotBeActivatedWithTheirPassword() throws Exception {
        // An attacker pre-registers the victim's address with the attacker's password...
        Signup signup = newSignup();
        register(signup, "Attacker2026").andExpect(status().isAccepted());
        // ...the victim, who reads the inbox, enters the code with their own password: refused, nothing created.
        verify(signup.email(), CapturingEmailSender.lastCodeFor(signup.email()), PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGISTRATION_PASSWORD_MISMATCH"));
        assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", signup.email())).isZero();

        // The victim registers again: their details replace the attacker's, and only their password activates it.
        expireCooldown(signup.email());
        register(signup, PASSWORD).andExpect(status().isAccepted());
        String code = CapturingEmailSender.lastCodeFor(signup.email());
        verify(signup.email(), code, "Attacker2026").andExpect(status().isBadRequest());
        verify(signup.email(), code, PASSWORD).andExpect(status().isCreated());
        assertThat(login(mockMvc, signup.email(), "Attacker2026").getResponse().getStatus()).isEqualTo(401);
    }

    // ---------- username conflicts at account creation ----------

    @Test
    void aUsernameTakenMeanwhileIsReportedAsAFieldErrorAndCanBeChangedWithTheSameCode() throws Exception {
        int n = AuthTestSupport.next();
        String shared = "shared" + n;
        Signup first = new Signup(shared, "first" + n + "@example.test", "8" + String.format("%09d", n));
        Signup second = new Signup(shared, "second" + n + "@example.test", "8" + String.format("%09d", AuthTestSupport.next()));
        register(first, PASSWORD).andExpect(status().isAccepted());
        register(second, PASSWORD).andExpect(status().isAccepted());
        String secondCode = CapturingEmailSender.lastCodeFor(second.email());

        verify(first.email(), CapturingEmailSender.lastCodeFor(first.email()), PASSWORD).andExpect(status().isCreated());
        verify(second.email(), secondCode, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_EXISTS"))
                .andExpect(jsonPath("$.errors[0].field").value("username"));

        // The pending registration and its code are intact: no attempt counted, not consumed.
        assertThat(count("SELECT failed_attempts FROM pending_registrations WHERE email = ?", second.email())).isZero();
        assertThat(count("SELECT COUNT(*) FROM pending_registrations WHERE email = ? AND consumed_at IS NULL", second.email())).isEqualTo(1);

        // An invalid replacement is a field error, still not an attempt.
        verifyWithUsername(second.email(), secondCode, PASSWORD, "admin")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("username"));
        assertThat(count("SELECT failed_attempts FROM pending_registrations WHERE email = ?", second.email())).isZero();

        verifyWithUsername(second.email(), secondCode, PASSWORD, "Renamed" + n)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.username").value("renamed" + n));
        assertThat(count("SELECT COUNT(*) FROM users WHERE username = ?", shared)).isEqualTo(1);
    }

    @Test
    void twoSignupsRacingForOneUsernameNeverCreateTwoAccountsOrLoseTheLoser() throws Exception {
        int n = AuthTestSupport.next();
        String shared = "race" + n;
        Signup a = new Signup(shared, "racea" + n + "@example.test", "8" + String.format("%09d", n));
        Signup b = new Signup(shared, "raceb" + n + "@example.test", "8" + String.format("%09d", AuthTestSupport.next()));
        register(a, PASSWORD).andExpect(status().isAccepted());
        register(b, PASSWORD).andExpect(status().isAccepted());
        String codeA = CapturingEmailSender.lastCodeFor(a.email());
        String codeB = CapturingEmailSender.lastCodeFor(b.email());

        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Future<MvcResult> fa = pool.submit(() -> { start.await(); return verify(a.email(), codeA, PASSWORD).andReturn(); });
            java.util.concurrent.Future<MvcResult> fb = pool.submit(() -> { start.await(); return verify(b.email(), codeB, PASSWORD).andReturn(); });
            start.countDown();
            List<MvcResult> results = List.of(fa.get(), fb.get());

            assertThat(results).extracting(r -> r.getResponse().getStatus()).containsExactlyInAnyOrder(201, 409);
            MvcResult loser = results.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst().orElseThrow();
            assertThat(JsonPath.<String>read(loser.getResponse().getContentAsString(), "$.code")).isEqualTo("ACCOUNT_EXISTS");
            assertThat(JsonPath.<List<String>>read(loser.getResponse().getContentAsString(), "$.errors[*].field")).contains("username");
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("SELECT COUNT(*) FROM users WHERE username = ?", shared)).isEqualTo(1);
        // The loser's registration survives, unconsumed, and can finish with another username.
        String loserEmail = count("SELECT COUNT(*) FROM users WHERE email = ?", a.email()) == 1 ? b.email() : a.email();
        String loserCode = loserEmail.equals(a.email()) ? codeA : codeB;
        assertThat(count("SELECT COUNT(*) FROM pending_registrations WHERE email = ? AND consumed_at IS NULL", loserEmail)).isEqualTo(1);
        verifyWithUsername(loserEmail, loserCode, PASSWORD, "racewinner" + n).andExpect(status().isCreated());
    }

    // ---------- resend ----------

    @Test
    void resendHasACooldownAndInvalidatesThePreviousCode() throws Exception {
        Signup signup = newSignup();
        register(signup, PASSWORD).andExpect(status().isAccepted());
        String first = CapturingEmailSender.lastCodeFor(signup.email());

        resend(signup.email()).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OTP_RESEND_COOLDOWN"));
        assertThat(CapturingEmailSender.inbox(signup.email())).hasSize(1);

        expireCooldown(signup.email());
        resend(signup.email()).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.otp").doesNotExist());
        String second = CapturingEmailSender.lastCodeFor(signup.email());
        assertThat(CapturingEmailSender.inbox(signup.email())).hasSize(2);
        if (!first.equals(second)) {
            verify(signup.email(), first, PASSWORD).andExpect(status().isBadRequest());
        }
        verify(signup.email(), second, PASSWORD).andExpect(status().isCreated());
    }

    @Test
    void atMostThreeResendsPerHourPerEmail() throws Exception {
        Signup signup = newSignup();
        register(signup, PASSWORD).andExpect(status().isAccepted());
        for (int i = 0; i < 3; i++) {
            expireCooldown(signup.email());
            resend(signup.email()).andExpect(status().isAccepted());
        }
        expireCooldown(signup.email());
        resend(signup.email()).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OTP_RESEND_LIMIT"));
        // Re-submitting the registration form does not bypass the limit either.
        register(signup, PASSWORD).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OTP_RESEND_LIMIT"));
        assertThat(CapturingEmailSender.inbox(signup.email())).hasSize(4);
    }

    @Test
    void resendAndVerifyAnswerUnknownEmailsExactlyLikeRealRegistrations() throws Exception {
        Signup real = newSignup();
        register(real, PASSWORD).andExpect(status().isAccepted());
        String unknown = "nobody" + AuthTestSupport.next() + "@example.test";

        // Resend: 202 with the same shape; nothing is sent for the unknown address.
        expireCooldown(real.email());
        resend(real.email()).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.verificationRequired").value(true));
        resend(unknown).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.verificationRequired").value(true))
                .andExpect(jsonPath("$.data.resendAvailableAt").isNotEmpty());
        assertThat(CapturingEmailSender.inbox(unknown)).isEmpty();
        // Cooldown applies to both.
        resend(real.email()).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OTP_RESEND_COOLDOWN"));
        resend(unknown).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OTP_RESEND_COOLDOWN"));

        // Verify: wrong codes get the same answer, and both lock after five.
        String wrong = wrongCode(CapturingEmailSender.lastCodeFor(real.email()));
        for (int i = 1; i <= 4; i++) {
            verify(real.email(), wrong, PASSWORD).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_INVALID"));
            verify(unknown, wrong, PASSWORD).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_INVALID"));
        }
        verify(real.email(), wrong, PASSWORD).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
        verify(unknown, wrong, PASSWORD).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
    }

    // ---------- sessions before / after verification ----------

    @Test
    void anUnverifiedSignupCannotLogInOrRefresh() throws Exception {
        Signup signup = newSignup();
        register(signup, PASSWORD).andExpect(status().isAccepted());

        MvcResult byEmail = login(mockMvc, signup.email(), PASSWORD);
        assertThat(byEmail.getResponse().getStatus()).isEqualTo(403);
        assertThat(byEmail.getResponse().getContentAsString()).contains("EMAIL_NOT_VERIFIED");
        assertThat(byEmail.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(login(mockMvc, signup.username(), PASSWORD).getResponse().getStatus()).isEqualTo(403);
        // Wrong password: the generic error, revealing nothing about the pending sign-up.
        MvcResult wrong = login(mockMvc, signup.email(), "WrongPass123");
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrong.getResponse().getContentAsString()).contains("INVALID_CREDENTIALS");

        // No refresh cookie was ever issued, so there is nothing to refresh.
        mockMvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest")).andExpect(status().isUnauthorized());
    }

    @Test
    void anAccountCreatedMeanwhileForThatEmailGetsTheNormalLoginErrorNotTheVerifyHint() throws Exception {
        Signup signup = newSignup();
        register(signup, PASSWORD).andExpect(status().isAccepted());
        // e.g. the same person then used "Continue with Google": an account now exists for the email.
        jdbc.update("""
                INSERT INTO users (full_name, username, email, password_hash, account_status, email_verified, created_at, updated_at)
                VALUES ('Google User', ?, ?, ?, 'ACTIVE', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""",
                "google_" + signup.username(), signup.email(), passwordEncoder.encode("UnusableRandom123"));

        MvcResult result = login(mockMvc, signup.email(), PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentAsString()).contains("INVALID_CREDENTIALS").doesNotContain("EMAIL_NOT_VERIFIED");
    }

    @Test
    void onlyTheNewestPendingSignupForAUsernameIsConsidered() throws Exception {
        int n = AuthTestSupport.next();
        String shared = "same" + n;
        Signup older = new Signup(shared, "older" + n + "@example.test", "8" + String.format("%09d", n));
        Signup newer = new Signup(shared, "newer" + n + "@example.test", "8" + String.format("%09d", AuthTestSupport.next()));
        register(older, "OlderPass2026").andExpect(status().isAccepted());
        jdbc.update("UPDATE pending_registrations SET updated_at = TIMESTAMPADD(MINUTE, -5, updated_at) WHERE email = ?", older.email());
        register(newer, PASSWORD).andExpect(status().isAccepted());

        assertThat(login(mockMvc, shared, PASSWORD).getResponse().getStatus()).isEqualTo(403);
        assertThat(login(mockMvc, shared, "OlderPass2026").getResponse().getStatus()).isEqualTo(401);
        // By email, each pending sign-up is found directly.
        assertThat(login(mockMvc, older.email(), "OlderPass2026").getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void legacyAccountsWithUnverifiedEmailsStillLogInNormally() throws Exception {
        // Accounts created before email OTP existed have email_verified = FALSE; they must keep working.
        int n = AuthTestSupport.next();
        String username = "legacy" + n;
        jdbc.update("""
                INSERT INTO users (full_name, username, email, phone, password_hash, account_status, email_verified, phone_verified, created_at, updated_at)
                VALUES ('Legacy User', ?, ?, ?, ?, 'ACTIVE', FALSE, FALSE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""",
                username, username + "@example.test", "8" + String.format("%09d", n), passwordEncoder.encode(PASSWORD));
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r WHERE u.username = ? AND r.role_name = 'BUYER'", username);

        MvcResult result = login(mockMvc, username, PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(JsonPath.<Boolean>read(result.getResponse().getContentAsString(), "$.data.user.emailVerified")).isFalse();
        mockMvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("fc_refresh", AuthTestSupport.refreshCookie(result))))
                .andExpect(status().isOk());
    }

    @Test
    void verificationRequestsAreValidated() throws Exception {
        mockMvc.perform(withUniqueIp(post("/api/v1/auth/register/verify")).contentType(MediaType.APPLICATION_JSON)
                        .content(verifyJson("not-an-email", "12ab", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify("nobody" + AuthTestSupport.next() + "@example.test", "123456", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));
    }

    // ---------- helpers ----------

    /** An existing account created directly (no recent registration emails or cooldowns for its address). */
    private Signup insertExistingAccount() {
        int n = AuthTestSupport.next();
        Signup account = new Signup("existing" + n, "existing" + n + "@example.test", "8" + String.format("%09d", n));
        jdbc.update("""
                INSERT INTO users (full_name, username, email, phone, password_hash, account_status, created_at, updated_at)
                VALUES ('Existing User', ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))""",
                account.username(), account.email(), account.phone(), passwordEncoder.encode(PASSWORD));
        return account;
    }

    private Signup newSignup() {
        int n = AuthTestSupport.next();
        return new Signup("signup" + n, "signup" + n + "@example.test", "8" + String.format("%09d", n));
    }

    private ResultActions register(Signup signup, String password) throws Exception {
        return mockMvc.perform(withUniqueIp(post("/api/v1/auth/register")).contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(signup.username(), signup.email(), signup.phone(), password, password)));
    }

    private ResultActions verify(String email, String otp, String password) throws Exception {
        return mockMvc.perform(withUniqueIp(post("/api/v1/auth/register/verify")).contentType(MediaType.APPLICATION_JSON)
                .content(verifyJson(email, otp, password)));
    }

    private ResultActions verifyWithUsername(String email, String otp, String password, String username) throws Exception {
        return mockMvc.perform(withUniqueIp(post("/api/v1/auth/register/verify")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"otp\":\"%s\",\"password\":\"%s\",\"username\":\"%s\"}"
                        .formatted(email, otp, password, username)));
    }

    private ResultActions resend(String email) throws Exception {
        return mockMvc.perform(withUniqueIp(post("/api/v1/auth/register/resend")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    /** Moves the last send 2 minutes into the past, as if the 60-second cooldown had elapsed. */
    private void expireCooldown(String email) {
        jdbc.update("UPDATE pending_registrations SET last_sent_at = TIMESTAMPADD(MINUTE, -2, last_sent_at) WHERE email = ?", email);
    }

    private static String wrongCode(String code) {
        return code.equals("000000") ? "000001" : "000000";
    }

    private int count(String sql, Object... args) {
        List<Integer> values = jdbc.queryForList(sql, Integer.class, args);
        return values.isEmpty() || values.getFirst() == null ? 0 : values.getFirst();
    }

    private static String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
