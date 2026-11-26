package com.farmcrop.auth;

import com.farmcrop.auth.AuthTestSupport.Account;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static com.farmcrop.auth.AuthTestSupport.PASSWORD;
import static com.farmcrop.auth.AuthTestSupport.bearer;
import static com.farmcrop.auth.AuthTestSupport.login;
import static com.farmcrop.auth.AuthTestSupport.register;
import static com.farmcrop.auth.AuthTestSupport.registerJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    // ---------- registration ----------

    @Test
    void registrationCreatesBuyerWithHashedPasswordAndSecureRefreshCookie() throws Exception {
        int n = AuthTestSupport.next();
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Reg" + n, "Reg" + n + "@Example.test", "+91 98765 4" + String.format("%04d", n), PASSWORD, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("reg" + n))
                .andExpect(jsonPath("$.data.user.email").value("reg" + n + "@example.test"))
                .andExpect(jsonPath("$.data.user.roles").value(org.hamcrest.Matchers.contains("BUYER")))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("fc_refresh=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")));

        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = ?", String.class, "reg" + n);
        assertThat(hash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    }

    @Test
    void registrationCannotGrantItselfSellerOrAdmin() throws Exception {
        int n = AuthTestSupport.next();
        String body = """
                {"fullName":"Sneaky","username":"sneaky%d","email":"sneaky%d@example.test","phone":"8%09d",
                 "password":"%s","confirmPassword":"%s","roles":["ADMIN","SELLER"],"role":"ADMIN","accountStatus":"ACTIVE"}
                """.formatted(n, n, n, PASSWORD, PASSWORD);
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.roles").value(org.hamcrest.Matchers.contains("BUYER")));
    }

    @Test
    void registrationRejectsDuplicateUsernameEmailAndPhone() throws Exception {
        Account existing = register(mockMvc);
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(existing.username().toUpperCase(), existing.email().toUpperCase(), existing.phone(), PASSWORD, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_EXISTS"))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("username", "email", "phone")));
    }

    @Test
    void registrationValidatesInputFieldByField() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("validname1", "valid1@example.test", "9123456789", PASSWORD, "Different99")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("confirmPassword")));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("validname2", "valid2@example.test", "9123456780", "short", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("admin", "valid3@example.test", "9123456781", PASSWORD, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("username"));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("validname4", "not-an-email", "12", PASSWORD, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")));
    }

    // ---------- login ----------

    @Test
    void loginAcceptsUsernameOrEmailCaseInsensitively() throws Exception {
        Account account = register(mockMvc);
        assertThat(login(mockMvc, account.username().toUpperCase(), PASSWORD).getResponse().getStatus()).isEqualTo(200);
        MvcResult byEmail = login(mockMvc, account.email(), PASSWORD);
        assertThat(byEmail.getResponse().getStatus()).isEqualTo(200);
        assertThat(AuthTestSupport.refreshCookie(byEmail)).isNotBlank();
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameGenericError() throws Exception {
        Account account = register(mockMvc);
        MvcResult wrongPassword = login(mockMvc, account.username(), "WrongPass123");
        MvcResult unknownUser = login(mockMvc, "nobody-" + AuthTestSupport.next(), "WrongPass123");

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownUser.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getResponse().getContentAsString()).contains("INVALID_CREDENTIALS");
        assertThat(unknownUser.getResponse().getContentAsString()).contains("Invalid username/email or password.");
    }

    @Test
    void repeatedFailuresAreThrottledEvenForTheCorrectPassword() throws Exception {
        Account account = register(mockMvc);
        for (int i = 0; i < 5; i++) {
            assertThat(login(mockMvc, account.username(), "WrongPass123").getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult blocked = login(mockMvc, account.username(), PASSWORD);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
        assertThat(blocked.getResponse().getContentAsString()).contains("TOO_MANY_ATTEMPTS");
    }

    @Test
    void suspendedAccountsCannotLogInOrUseExistingTokens() throws Exception {
        Account account = register(mockMvc);
        jdbc.update("UPDATE users SET account_status = 'SUSPENDED' WHERE username = ?", account.username());

        // Correct password: told it is suspended. Wrong password: generic error, no status leak.
        MvcResult correct = login(mockMvc, account.username(), PASSWORD);
        assertThat(correct.getResponse().getStatus()).isEqualTo(403);
        assertThat(correct.getResponse().getContentAsString()).contains("ACCOUNT_SUSPENDED");
        assertThat(login(mockMvc, account.username(), "WrongPass123").getResponse().getStatus()).isEqualTo(401);

        // An access token issued before suspension stops working immediately.
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(account.accessToken())))
                .andExpect(status().isUnauthorized());
        // And the refresh token cannot mint new access tokens.
        refresh(account.refreshCookie()).andExpect(status().isUnauthorized());
    }

    // ---------- access tokens & profile ----------

    @Test
    void profileRequiresAValidAccessToken() throws Exception {
        Account account = register(mockMvc);
        mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());

        String token = account.accessToken();
        String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(account.username()))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"));
    }

    @Test
    void usersCanUpdateOnlyEditableProfileFields() throws Exception {
        Account account = register(mockMvc);
        Account other = register(mockMvc);

        mockMvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(account.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ramesh Kumar\",\"phone\":\"%s\",\"preferredLanguage\":\"hi\",\"username\":\"hijack\",\"roles\":[\"ADMIN\"]}"
                                .formatted(account.phone())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Ramesh Kumar"))
                .andExpect(jsonPath("$.data.preferredLanguage").value("hi"))
                .andExpect(jsonPath("$.data.username").value(account.username()))
                .andExpect(jsonPath("$.data.roles").value(org.hamcrest.Matchers.contains("BUYER")));

        mockMvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer(account.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ramesh Kumar\",\"phone\":\"%s\"}".formatted(other.phone())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("phone"));
    }

    @Test
    void buyersCannotReachAdminEndpoints() throws Exception {
        Account account = register(mockMvc);
        mockMvc.perform(get("/api/v1/admin/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(account.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // ---------- refresh tokens ----------

    @Test
    void refreshRotatesTheCookieAndIssuesANewAccessToken() throws Exception {
        Account account = register(mockMvc);
        MvcResult result = refresh(account.refreshCookie()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value(account.username()))
                .andReturn();
        String rotated = AuthTestSupport.refreshCookie(result);
        assertThat(rotated).isNotBlank().isNotEqualTo(account.refreshCookie());
        refresh(rotated).andExpect(status().isOk());
    }

    @Test
    void replayingARotatedRefreshTokenRevokesTheWholeSession() throws Exception {
        Account account = register(mockMvc);
        String successor = AuthTestSupport.refreshCookie(refresh(account.refreshCookie()).andReturn());
        // Move the rotation outside the concurrent-tab grace window to simulate a stolen, replayed token.
        jdbc.update("UPDATE refresh_tokens SET revoked_at = DATEADD('HOUR', -1, revoked_at) WHERE token_hash = ?",
                sha256(account.refreshCookie()));

        refresh(account.refreshCookie())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_REVOKED"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        // The legitimate successor was revoked too.
        refresh(successor).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentTabRefreshIsToleratedWithoutKillingTheSession() throws Exception {
        Account account = register(mockMvc);
        String successor = AuthTestSupport.refreshCookie(refresh(account.refreshCookie()).andReturn());
        refresh(account.refreshCookie())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_RACE"));
        refresh(successor).andExpect(status().isOk());
    }

    @Test
    void cookieEndpointsRequireTheAntiCsrfHeader() throws Exception {
        Account account = register(mockMvc);
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("fc_refresh", account.refreshCookie())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_CHECK_FAILED"));
        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("fc_refresh", account.refreshCookie())))
                .andExpect(status().isForbidden());
        // The token was not consumed by the rejected attempts.
        refresh(account.refreshCookie()).andExpect(status().isOk());
    }

    @Test
    void refreshWithoutACookieIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        Account account = register(mockMvc);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("fc_refresh", account.refreshCookie())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        refresh(account.refreshCookie()).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String cookieValue) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(new Cookie("fc_refresh", cookieValue)));
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
