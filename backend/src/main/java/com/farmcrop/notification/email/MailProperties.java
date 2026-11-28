package com.farmcrop.notification.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * SMTP settings (prefix {@code farmcrop.mail}), filled from MAIL_* environment variables or the git-ignored
 * backend/.env. Deliberately not {@code spring.mail.*}, so Spring Boot does not auto-create a half-configured sender.
 */
@ConfigurationProperties(prefix = "farmcrop.mail")
public record MailProperties(
        String host,
        Integer port,
        String username,
        String password,
        String fromAddress,
        String fromName,
        Boolean starttls,
        Duration timeout
) {

    public MailProperties {
        host = trim(host);
        username = trim(username);
        fromAddress = trim(fromAddress);
        fromName = fromName == null || fromName.isBlank() ? "FarmCrop" : fromName.trim();
        if (port == null) port = 587;
        if (starttls == null) starttls = true;
        if (timeout == null) timeout = Duration.ofSeconds(10);
    }

    public boolean configured() {
        return !host.isEmpty() && !fromAddress.isEmpty();
    }

    @Override
    public String toString() {
        return "MailProperties[host=" + host + ", port=" + port + ", username=" + (username.isEmpty() ? "" : "***")
                + ", password=***, fromAddress=" + fromAddress + ", fromName=" + fromName + "]";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
