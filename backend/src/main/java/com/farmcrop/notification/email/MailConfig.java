package com.farmcrop.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    EmailSender emailSender(MailProperties properties) {
        SmtpEmailSender sender = new SmtpEmailSender(properties);
        if (!sender.isConfigured()) {
            log.warn("Email delivery is not configured (MAIL_HOST / MAIL_FROM_ADDRESS). Email registration will be unavailable.");
        }
        return sender;
    }
}
