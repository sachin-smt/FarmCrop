package com.farmcrop.notification.email;

/**
 * Sends transactional email. Implementations must never log message bodies (they contain one-time codes).
 * {@link #isConfigured()} is false when no provider is set up; callers must then fail visibly instead of
 * pretending that an email was sent.
 */
public interface EmailSender {

    boolean isConfigured();

    /** @throws EmailDeliveryException if the provider rejected or could not accept the message. */
    void send(EmailMessage message);
}
