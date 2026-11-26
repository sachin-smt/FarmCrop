package com.farmcrop.auth;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Helpers shared by authentication tests. Every call uses unique identities and client IPs so tests stay independent. */
public final class AuthTestSupport {

    public static final String PASSWORD = "Harvest2026";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    public record Account(String username, String email, String phone, String accessToken, String refreshCookie) {
    }

    private AuthTestSupport() {
    }

    public static int next() {
        return SEQUENCE.incrementAndGet();
    }

    /** A request builder with a unique client IP, so login throttling in one test never affects another. */
    public static MockHttpServletRequestBuilder withUniqueIp(MockHttpServletRequestBuilder builder) {
        int n = next();
        return builder.with(request -> {
            request.setRemoteAddr("10.1." + (n / 250) + "." + (n % 250 + 1));
            return request;
        });
    }

    public static String registerJson(String username, String email, String phone, String password, String confirm) {
        return """
                {"fullName":"Test Farmer","username":"%s","email":"%s","phone":"%s","password":"%s","confirmPassword":"%s"}
                """.formatted(username, email, phone, password, confirm);
    }

    public static Account register(MockMvc mockMvc) throws Exception {
        int n = next();
        String username = "user" + n;
        String email = "user" + n + "@example.test";
        String phone = "9" + String.format("%09d", n);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(username, email, phone, PASSWORD, PASSWORD)))
                .andReturn();
        if (result.getResponse().getStatus() != 201) {
            throw new IllegalStateException("Registration failed: " + result.getResponse().getContentAsString());
        }
        return new Account(username, email, phone, accessToken(result), refreshCookie(result));
    }

    public static MvcResult login(MockMvc mockMvc, String identifier, String password) throws Exception {
        return mockMvc.perform(withUniqueIp(post("/api/v1/auth/login"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usernameOrEmail\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password)))
                .andReturn();
    }

    public static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    /** Raw value of the fc_refresh cookie from the Set-Cookie header, or null. */
    public static String refreshCookie(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        if (header == null || !header.startsWith("fc_refresh=")) return null;
        String value = header.substring("fc_refresh=".length(), header.indexOf(';'));
        return value.isEmpty() ? null : value;
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
