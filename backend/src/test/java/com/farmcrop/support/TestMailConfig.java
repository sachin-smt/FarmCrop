package com.farmcrop.support;

import com.farmcrop.notification.email.EmailSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Picked up by component scanning in every test context: no test can ever send a real email. */
@Configuration
public class TestMailConfig {

    @Bean
    @Primary
    EmailSender capturingEmailSender() {
        return new CapturingEmailSender();
    }
}
