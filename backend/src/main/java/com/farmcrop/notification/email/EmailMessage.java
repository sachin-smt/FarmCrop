package com.farmcrop.notification.email;

/** A transactional email. {@code html} may be null for text-only messages. Never log the body: it can contain codes. */
public record EmailMessage(String to, String subject, String text, String html) {

    @Override
    public String toString() {
        return "EmailMessage[to=" + to + ", subject=" + subject + "]";
    }
}
