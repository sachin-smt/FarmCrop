package com.farmcrop.notification.email;

/** The email provider could not accept a message. The message text is safe to log (no codes or credentials). */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
