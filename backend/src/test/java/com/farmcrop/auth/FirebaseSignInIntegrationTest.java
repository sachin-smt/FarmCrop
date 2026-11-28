package com.farmcrop.auth;

import com.farmcrop.auth.AuthTestSupport.Account;
import com.farmcrop.support.CapturingEmailSender;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static com.farmcrop.auth.AuthTestSupport.PASSWORD;
import static com.farmcrop.auth.AuthTestSupport.bearer;
import static com.farmcrop.auth.AuthTestSupport.login;
import static com.farmcrop.auth.AuthTestSupport.register;
import static com.farmcrop.auth.AuthTestSupport.registerJson;
import static com.farmcrop.auth.AuthTestSupport.withUniqueIp;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FirebaseTestTokens.Config.class)
class FirebaseSignInIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------- Google ----------

    @Test
    void firstGoogleSignInCreatesAVerifiedBuyerAndLaterSignInsReuseIt() throws Exception {
        int n = AuthTestSupport.next();
        String email = "Asha" + n + "@Gmail.test";

        MvcResult first = google(FirebaseTestTokens.google("g-" + n, email, true).sign())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("asha" + n + "@gmail.test"))
                .andExpect(jsonPath("$.data.user.emailVerified").value(true))
                .andExpect(jsonPath("$.data.user.fullName").value("Asha Kumari"))
                .andExpect(jsonPath("$.data.user.roles").value(contains("BUYER")))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("fc_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andReturn();
        int userId = userId(first);
        assertThat(JsonPath.<String>read(first.getResponse().getContentAsString(), "$.data.user.username")).startsWith("asha" + n + "_");

        // Even if the Google email changes later, the Google account id keeps pointing at the same user.
        MvcResult second = google(FirebaseTestTokens.google("g-" + n, "renamed" + n + "@gmail.test", true).sign())
                .andExpect(status().isOk()).andReturn();
        assertThat(userId(second)).isEqualTo(userId);
        assertThat(count("SELECT COUNT(*) FROM user_auth_identities WHERE provider = 'GOOGLE' AND provider_subject = ?", "g-" + n)).isEqualTo(1);

        // Google users have no usable password.
        assertThat(login(mockMvc, "asha" + n + "@gmail.test", PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void googleSignInWithAnExistingEmailLinksToThatAccountWithoutADuplicate() throws Exception {
        Account existing = register(mockMvc);
        int before = count("SELECT COUNT(*) FROM users");

        MvcResult result = google(FirebaseTestTokens.google("g-link-" + existing.username(), existing.email().toUpperCase(), true).sign())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value(existing.username()))
                .andExpect(jsonPath("$.data.user.emailVerified").value(true))
                .andReturn();

        assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(before);
        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.data.user.phone")).isEqualTo(existing.phone());
        // The original password keeps working.
        assertThat(login(mockMvc, existing.username(), PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void anAccountAlreadyLinkedToAnotherGoogleAccountIsNotTakenOver() throws Exception {
        Account existing = register(mockMvc);
        google(FirebaseTestTokens.google("g-owner-" + existing.username(), existing.email(), true).sign()).andExpect(status().isOk());

        google(FirebaseTestTokens.google("g-other-" + existing.username(), existing.email(), true).sign())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GOOGLE_ACCOUNT_MISMATCH"));
    }

    @Test
    void adminAccountsCannotUseGoogleSignIn() throws Exception {
        Account admin = register(mockMvc);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r WHERE u.username = ? AND r.role_name = 'ADMIN'",
                admin.username());

        google(FirebaseTestTokens.google("g-admin-" + admin.username(), admin.email(), true).sign())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_PASSWORD_LOGIN_REQUIRED"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(count("SELECT COUNT(*) FROM user_auth_identities WHERE provider_subject = ?", "g-admin-" + admin.username())).isZero();
    }

    @Test
    void unverifiedGoogleEmailIsRejected() throws Exception {
        int n = AuthTestSupport.next();
        google(FirebaseTestTokens.google("g-unverified-" + n, "unverified" + n + "@gmail.test", false).sign())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GOOGLE_EMAIL_NOT_VERIFIED"));
        assertThat(count("SELECT COUNT(*) FROM users WHERE email = ?", "unverified" + n + "@gmail.test")).isZero();
    }

    // ---------- phone OTP ----------

    @Test
    void firstPhoneSignInCreatesABuyerWithoutEmailAndLaterSignInsReuseIt() throws Exception {
        String phone = indianMobile();
        MvcResult first = phone(FirebaseTestTokens.phone(phone).sign())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.phone").value(phone))
                .andExpect(jsonPath("$.data.user.phoneVerified").value(true))
                .andExpect(jsonPath("$.data.user.email").doesNotExist())
                .andExpect(jsonPath("$.data.user.roles").value(contains("BUYER")))
                .andReturn();

        // A new OTP verification (new token) signs in to the same account.
        MvcResult second = phone(FirebaseTestTokens.phone(phone).sign()).andExpect(status().isOk()).andReturn();
        assertThat(userId(second)).isEqualTo(userId(first));
        assertThat(count("SELECT COUNT(*) FROM users WHERE phone = ?", phone)).isEqualTo(1);

        // The session behaves like any other: profile and refresh work.
        String accessToken = JsonPath.read(second.getResponse().getContentAsString(), "$.data.accessToken");
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value(phone));
        mockMvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("fc_refresh", AuthTestSupport.refreshCookie(second))))
                .andExpect(status().isOk());
    }

    @Test
    void phoneSignInIsRefusedForAnExistingAccountThatNeverVerifiedTheNumber() throws Exception {
        int n = AuthTestSupport.next();
        String national = "8" + String.format("%09d", n);
        registerWithPhone("phoneuser" + n, national);
        int before = count("SELECT COUNT(*) FROM users");

        phone(FirebaseTestTokens.phone("+91" + national).sign())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_NOT_VERIFIED_FOR_ACCOUNT"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        // Not attached, not marked verified, and no duplicate account created for the number.
        assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT phone_verified FROM users WHERE username = ?", Boolean.class, "phoneuser" + n)).isFalse();
        assertThat(jdbc.queryForObject("SELECT phone FROM users WHERE username = ?", String.class, "phoneuser" + n)).isEqualTo(national);
        // Retrying with a new OTP verification changes nothing: the account holder must use password login.
        phone(FirebaseTestTokens.phone("+91" + national).sign()).andExpect(status().isConflict());
    }

    @Test
    void anAccountWithAVerifiedNumberSignsInByPhoneWithItsDataAndRolesUnchanged() throws Exception {
        int n = AuthTestSupport.next();
        String national = "8" + String.format("%09d", n);
        registerWithPhone("seller" + n, national);
        jdbc.update("UPDATE users SET phone_verified = TRUE, preferred_language = 'hi' WHERE username = ?", "seller" + n);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r WHERE u.username = ? AND r.role_name = 'SELLER'",
                "seller" + n);
        int before = count("SELECT COUNT(*) FROM users");

        phone(FirebaseTestTokens.phone("+91" + national).sign())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value("seller" + n))
                .andExpect(jsonPath("$.data.user.email").value("seller" + n + "@example.test"))
                .andExpect(jsonPath("$.data.user.phone").value(national))
                .andExpect(jsonPath("$.data.user.preferredLanguage").value("hi"))
                .andExpect(jsonPath("$.data.user.roles").value(containsInAnyOrder("BUYER", "SELLER")));
        assertThat(count("SELECT COUNT(*) FROM users")).isEqualTo(before);

        // And registration treats +91XXXXXXXXXX as the same number (no duplicate account): reported once the
        // new email is proven, at verification.
        assertThat(AuthTestSupport.startRegistration(mockMvc, "dupe" + n, "dupe" + n + "@example.test", "+91" + national)
                .getResponse().getStatus()).isEqualTo(202);
        MvcResult dupe = AuthTestSupport.verifyRegistration(mockMvc, "dupe" + n + "@example.test",
                CapturingEmailSender.lastCodeFor("dupe" + n + "@example.test"), PASSWORD);
        assertThat(dupe.getResponse().getStatus()).isEqualTo(409);
        assertThat(dupe.getResponse().getContentAsString()).contains("\"field\":\"phone\"");
    }

    @Test
    void googleLinkingKeepsTheExistingRolesAndProfile() throws Exception {
        Account seller = register(mockMvc);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r WHERE u.username = ? AND r.role_name = 'SELLER'",
                seller.username());

        google(FirebaseTestTokens.google("g-seller-" + seller.username(), seller.email(), true).sign())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value(seller.username()))
                .andExpect(jsonPath("$.data.user.fullName").value("Test Farmer"))
                .andExpect(jsonPath("$.data.user.roles").value(containsInAnyOrder("BUYER", "SELLER")));
        assertThat(count("SELECT COUNT(*) FROM user_roles ur JOIN users u ON u.id = ur.user_id WHERE u.username = ?", seller.username()))
                .isEqualTo(2);
    }

    @Test
    void googleSignInWithAnUnverifiedEmailNeverLinksToAnExistingAccount() throws Exception {
        Account existing = register(mockMvc);
        google(FirebaseTestTokens.google("g-unverified-link-" + existing.username(), existing.email(), false).sign())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GOOGLE_EMAIL_NOT_VERIFIED"));
        assertThat(count("SELECT COUNT(*) FROM user_auth_identities WHERE provider_subject = ?", "g-unverified-link-" + existing.username())).isZero();
    }

    @Test
    void nonIndianNumbersAreRejected() throws Exception {
        phone(FirebaseTestTokens.phone("+14155550" + String.format("%03d", AuthTestSupport.next() % 1000)).sign())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_PHONE_NUMBER"));
    }

    @Test
    void suspendedAccountsCannotSignInByPhone() throws Exception {
        String phone = indianMobile();
        phone(FirebaseTestTokens.phone(phone).sign()).andExpect(status().isOk());
        jdbc.update("UPDATE users SET account_status = 'SUSPENDED' WHERE phone = ?", phone);

        phone(FirebaseTestTokens.phone(phone).sign())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_SUSPENDED"));
    }

    // ---------- "verify my phone" for signed-in users ----------

    @Test
    void verifyingThePhoneWhileSignedInEnablesOtpLoginForThatAccount() throws Exception {
        Account account = register(mockMvc); // phone stored as the national 10-digit form, unverified
        phone(FirebaseTestTokens.phone("+91" + account.phone()).sign()).andExpect(status().isConflict());

        verifyMyPhone(account.accessToken(), FirebaseTestTokens.phone("+91" + account.phone()).sign())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value(account.phone()))
                .andExpect(jsonPath("$.data.phoneVerified").value(true))
                .andExpect(jsonPath("$.data.username").value(account.username()));

        // Now mobile OTP login reaches the same account, and the password still works.
        phone(FirebaseTestTokens.phone("+91" + account.phone()).sign())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value(account.username()));
        assertThat(login(mockMvc, account.username(), PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void verifyingANewNumberSwitchesTheAccountToItWithoutTouchingRoles() throws Exception {
        Account seller = register(mockMvc);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r WHERE u.username = ? AND r.role_name = 'SELLER'",
                seller.username());
        String newNumber = indianMobile();

        verifyMyPhone(seller.accessToken(), FirebaseTestTokens.phone(newNumber).sign())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value(newNumber))
                .andExpect(jsonPath("$.data.phoneVerified").value(true))
                .andExpect(jsonPath("$.data.email").value(seller.email()))
                .andExpect(jsonPath("$.data.roles").value(containsInAnyOrder("BUYER", "SELLER")));
    }

    @Test
    void aNumberBelongingToAnotherAccountIsNeverMovedOrVerified() throws Exception {
        Account owner = register(mockMvc);
        Account other = register(mockMvc);

        verifyMyPhone(other.accessToken(), FirebaseTestTokens.phone("+91" + owner.phone()).sign())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_IN_USE"));

        assertThat(jdbc.queryForObject("SELECT phone FROM users WHERE username = ?", String.class, other.username())).isEqualTo(other.phone());
        assertThat(jdbc.queryForObject("SELECT phone_verified FROM users WHERE username = ?", Boolean.class, other.username())).isFalse();
        assertThat(jdbc.queryForObject("SELECT phone_verified FROM users WHERE username = ?", Boolean.class, owner.username())).isFalse();
    }

    @Test
    void phoneVerificationNeedsASignedInUserAndAFreshSingleUsePhoneToken() throws Exception {
        String token = FirebaseTestTokens.phone(indianMobile()).sign();
        mockMvc.perform(withUniqueIp(post("/api/v1/users/me/phone/verify")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + token + "\"}"))
                .andExpect(status().isUnauthorized());

        Account account = register(mockMvc);
        verifyMyPhone(account.accessToken(), FirebaseTestTokens.google("g-v-" + account.username(), account.email(), true).sign())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SIGN_IN_TOKEN"));
        verifyMyPhone(account.accessToken(), FirebaseTestTokens.phone(indianMobile()).authTime(Instant.now().minus(Duration.ofMinutes(20))).sign())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_EXPIRED"));
        verifyMyPhone(account.accessToken(), token).andExpect(status().isOk());
        verifyMyPhone(account.accessToken(), token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_TOKEN_ALREADY_USED"));
    }

    // ---------- token verification ----------

    @Test
    void aTokenCanBeExchangedOnlyOnce() throws Exception {
        String token = FirebaseTestTokens.phone(indianMobile()).sign();
        phone(token).andExpect(status().isOk());
        phone(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_TOKEN_ALREADY_USED"));
    }

    @Test
    void everySignInNeedsARecentVerification() throws Exception {
        phone(FirebaseTestTokens.phone(indianMobile()).authTime(Instant.now().minus(Duration.ofMinutes(20))).sign())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_EXPIRED"));
    }

    @Test
    void forgedOrMisdirectedTokensAreRejected() throws Exception {
        String[] bad = {
                FirebaseTestTokens.phone(indianMobile()).untrustedKey().sign(),
                FirebaseTestTokens.phone(indianMobile()).audience("someone-elses-project").sign(),
                FirebaseTestTokens.phone(indianMobile()).issuer("https://securetoken.google.com/someone-elses-project").sign(),
                FirebaseTestTokens.phone(indianMobile()).expiresAt(Instant.now().minus(Duration.ofMinutes(10))).sign(),
                FirebaseTestTokens.phone(indianMobile()).authTime(Instant.now().plus(Duration.ofMinutes(10))).sign(),
                // A Google token presented as a phone login, and vice versa.
                FirebaseTestTokens.google("g-cross-" + AuthTestSupport.next(), "cross@gmail.test", true).sign(),
                "not-a-jwt",
        };
        for (String token : bad) {
            phone(token).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_SIGN_IN_TOKEN"));
        }
        google(FirebaseTestTokens.phone(indianMobile()).sign())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_SIGN_IN_TOKEN"));
    }

    @Test
    void repeatedInvalidTokensFromOneAddressAreThrottled() throws Exception {
        String ip = "10.250.0." + (AuthTestSupport.next() % 250 + 1);
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/phone").with(r -> { r.setRemoteAddr(ip); return r; })
                            .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"forged-" + i + "\"}"))
                    .andExpect(status().isUnauthorized());
        }
        // Even a valid token is refused while the address is blocked.
        mockMvc.perform(post("/api/v1/auth/phone").with(r -> { r.setRemoteAddr(ip); return r; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + FirebaseTestTokens.phone(indianMobile()).sign() + "\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void requestValidationAndClientSuppliedIdentityAreIgnored() throws Exception {
        mockMvc.perform(withUniqueIp(post("/api/v1/auth/google")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // Extra fields (role, phone) in the body cannot influence the account.
        String phone = indianMobile();
        String body = "{\"idToken\":\"" + FirebaseTestTokens.phone(phone).sign()
                + "\",\"phone\":\"+919999999999\",\"roles\":[\"ADMIN\"],\"role\":\"ADMIN\"}";
        mockMvc.perform(withUniqueIp(post("/api/v1/auth/phone")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.phone").value(phone))
                .andExpect(jsonPath("$.data.user.roles").value(contains("BUYER")));
    }

    @Test
    void loginPageCanDiscoverWhichMethodsAreEnabled() throws Exception {
        mockMvc.perform(get("/api/v1/public/auth-providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.password").value(true))
                .andExpect(jsonPath("$.data.google").value(true))
                .andExpect(jsonPath("$.data.phone").value(true));
    }

    // ---------- helpers ----------

    private ResultActions google(String idToken) throws Exception {
        return exchange("/api/v1/auth/google", idToken);
    }

    private ResultActions phone(String idToken) throws Exception {
        return exchange("/api/v1/auth/phone", idToken);
    }

    private ResultActions verifyMyPhone(String accessToken, String idToken) throws Exception {
        return mockMvc.perform(withUniqueIp(post("/api/v1/users/me/phone/verify"))
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"" + idToken + "\"}"));
    }

    private ResultActions exchange(String path, String idToken) throws Exception {
        return mockMvc.perform(withUniqueIp(post(path)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"" + idToken + "\"}"));
    }

    /** A complete, email-verified password account with the given phone (stored exactly as given). */
    private void registerWithPhone(String username, String phone) throws Exception {
        String email = username + "@example.test";
        assertThat(AuthTestSupport.startRegistration(mockMvc, username, email, phone).getResponse().getStatus()).isEqualTo(202);
        assertThat(AuthTestSupport.verifyRegistration(mockMvc, email, CapturingEmailSender.lastCodeFor(email), PASSWORD)
                .getResponse().getStatus()).isEqualTo(201);
    }

    /** A unique Indian mobile starting with 7 (registration helpers use 8/9, so no accidental matches). */
    private static String indianMobile() {
        return "+917" + String.format("%09d", AuthTestSupport.next());
    }

    private static int userId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.user.id");
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
