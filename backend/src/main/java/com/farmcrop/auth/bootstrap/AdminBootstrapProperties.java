package com.farmcrop.auth.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Supplied via FARMCROP_ADMIN_USERNAME / FARMCROP_ADMIN_EMAIL / FARMCROP_ADMIN_PASSWORD. */
@ConfigurationProperties(prefix = "farmcrop.admin")
public record AdminBootstrapProperties(String username, String email, String password) {

    boolean isComplete() {
        return notBlank(username) && notBlank(email) && notBlank(password);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public String toString() {
        return "AdminBootstrapProperties[username=" + username + ", email=" + email + ", password=***]";
    }
}
