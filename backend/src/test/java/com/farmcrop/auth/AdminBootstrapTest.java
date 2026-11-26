package com.farmcrop.auth;

import com.farmcrop.auth.bootstrap.AdminBootstrapProperties;
import com.farmcrop.auth.bootstrap.AdminBootstrapRunner;
import com.farmcrop.auth.bootstrap.AdminBootstrapRunner.Outcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminBootstrapTest {

    private static final String ADMIN_PASSWORD = "Str0ng-Admin-Passphrase";

    @Autowired
    private AdminBootstrapRunner runner;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    /** One ordered scenario, because "an admin exists" is global state in the shared test database. */
    @Test
    void createsTheFirstAdminOnceAndNeverPromotesOrResets() throws Exception {
        assertThat(runner.bootstrap(new AdminBootstrapProperties("", "", ""))).isEqualTo(Outcome.NOT_CONFIGURED);
        assertThat(runner.bootstrap(new AdminBootstrapProperties("chiefadmin", "chief@example.test", "short")))
                .isEqualTo(Outcome.INVALID_CONFIGURATION);

        // An existing public account is never promoted, even if its name is configured.
        AuthTestSupport.Account squatter = AuthTestSupport.register(mockMvc);
        assertThat(runner.bootstrap(new AdminBootstrapProperties(squatter.username(), "other@example.test", ADMIN_PASSWORD)))
                .isEqualTo(Outcome.IDENTITY_TAKEN);
        assertThat(roleCount(squatter.username(), "ADMIN")).isZero();

        assertThat(runner.bootstrap(new AdminBootstrapProperties("ChiefAdmin", "Chief@Example.test", ADMIN_PASSWORD)))
                .isEqualTo(Outcome.CREATED);
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'chiefadmin'", String.class);
        assertThat(hash).startsWith("{bcrypt}");

        // A restart with different settings does not create a second admin or reset the first.
        assertThat(runner.bootstrap(new AdminBootstrapProperties("chiefadmin", "chief@example.test", "Another-Passphrase-99")))
                .isEqualTo(Outcome.ADMIN_ALREADY_EXISTS);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'chiefadmin'", String.class)).isEqualTo(hash);

        MvcResult login = AuthTestSupport.login(mockMvc, "chiefadmin", ADMIN_PASSWORD);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, AuthTestSupport.bearer(AuthTestSupport.accessToken(login))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles").value(org.hamcrest.Matchers.contains("ADMIN")));
    }

    private int roleCount(String username, String role) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_roles ur JOIN users u ON u.id = ur.user_id JOIN roles r ON r.id = ur.role_id
                WHERE u.username = ? AND r.role_name = ?""", Integer.class, username, role);
    }
}
