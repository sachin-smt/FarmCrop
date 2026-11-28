package com.farmcrop.notification.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmtpEmailSenderTest {

    @Test
    void withoutHostOrSenderNothingIsSentAndNothingIsPretended() {
        SmtpEmailSender sender = new SmtpEmailSender(new MailProperties("", null, null, null, "", null, null, null));
        assertThat(sender.isConfigured()).isFalse();
        assertThatThrownBy(() -> sender.send(new EmailMessage("a@example.test", "s", "t", null)))
                .isInstanceOf(EmailDeliveryException.class);
        assertThat(new SmtpEmailSender(new MailProperties("smtp.example.test", null, null, null, "", null, null, null)).isConfigured()).isFalse();
    }

    @Test
    void configuredWithHostAndSenderUsesFarmCropDisplayNameAndHidesCredentials() {
        MailProperties properties = new MailProperties("smtp.example.test", 587, "user", "secret-value", "no-reply@example.test", null, null, null);
        assertThat(new SmtpEmailSender(properties).isConfigured()).isTrue();
        assertThat(properties.fromName()).isEqualTo("FarmCrop");
        assertThat(properties.starttls()).isTrue();
        assertThat(properties.toString()).doesNotContain("secret-value").doesNotContain("user,");
    }

    @Test
    void emailMessagesNeverPrintTheirBody() {
        assertThat(new EmailMessage("a@example.test", "Verify", "code 123456", "<b>123456</b>").toString()).doesNotContain("123456");
    }
}
