package com.farmcrop.notification.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** SMTP delivery (any provider: Brevo, Amazon SES SMTP, Mailgun, Zoho, Gmail app password, ...). */
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final MailProperties properties;
    private final JavaMailSenderImpl mailSender;

    public SmtpEmailSender(MailProperties properties) {
        this.properties = properties;
        this.mailSender = properties.configured() ? build(properties) : null;
    }

    @Override
    public boolean isConfigured() {
        return mailSender != null;
    }

    @Override
    public void send(EmailMessage message) {
        if (mailSender == null) throw new EmailDeliveryException("Email delivery is not configured.", null);
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, message.html() != null, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.fromAddress(), properties.fromName());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            if (message.html() != null) helper.setText(message.text(), message.html());
            else helper.setText(message.text());
            mailSender.send(mime);
            log.info("Sent email '{}' via SMTP", message.subject());
        } catch (MailException | MessagingException | UnsupportedEncodingException ex) {
            // Exception messages from JavaMail never contain the body; still, do not log the recipient's code.
            log.warn("SMTP delivery failed: {}", ex.getClass().getSimpleName());
            throw new EmailDeliveryException("The email could not be sent.", ex);
        }
    }

    private static JavaMailSenderImpl build(MailProperties properties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(properties.host());
        sender.setPort(properties.port());
        if (!properties.username().isEmpty()) {
            sender.setUsername(properties.username());
            sender.setPassword(properties.password());
        }
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties javaMail = sender.getJavaMailProperties();
        String timeout = String.valueOf(properties.timeout().toMillis());
        javaMail.put("mail.transport.protocol", "smtp");
        javaMail.put("mail.smtp.auth", String.valueOf(!properties.username().isEmpty()));
        javaMail.put("mail.smtp.connectiontimeout", timeout);
        javaMail.put("mail.smtp.timeout", timeout);
        javaMail.put("mail.smtp.writetimeout", timeout);
        if (properties.port() == 465) {
            javaMail.put("mail.smtp.ssl.enable", "true");          // implicit TLS
        } else if (properties.starttls()) {
            javaMail.put("mail.smtp.starttls.enable", "true");
            javaMail.put("mail.smtp.starttls.required", "true");  // never fall back to plaintext
        }
        javaMail.put("mail.smtp.ssl.checkserveridentity", "true");
        return sender;
    }
}
