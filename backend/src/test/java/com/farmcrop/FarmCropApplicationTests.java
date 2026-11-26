package com.farmcrop;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FarmCropApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loadsApplicationContextWithIsolatedTestDatabase() {
        String url = jdbcTemplate.execute((java.sql.Connection connection) -> connection.getMetaData().getURL());
        assertThat(url).startsWith("jdbc:h2:mem:");
    }

    @Test
    void flywayMigrationsCreateUserSchemaAndSeedRoles() {
        List<String> roles = jdbcTemplate.queryForList("SELECT role_name FROM roles ORDER BY role_name", String.class);
        assertThat(roles).containsExactly("ADMIN", "BUYER", "SELLER");

        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\" = TRUE", Integer.class);
        assertThat(applied).isPositive();
    }
}
