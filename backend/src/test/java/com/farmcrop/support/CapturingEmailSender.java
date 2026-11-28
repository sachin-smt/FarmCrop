package com.farmcrop.support;

import com.farmcrop.notification.email.EmailDeliveryException;
import com.farmcrop.notification.email.EmailMessage;
import com.farmcrop.notification.email.EmailSender;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test-only email sender: records messages in memory instead of delivering them (no network, no real email).
 * Tests read the code a user "received" with {@link #lastCodeFor(String)}.
 */
public class CapturingEmailSender implements EmailSender {

    private static final Pattern CODE = Pattern.compile("verification code is: ([0-9]{6})");
    private static final Map<String, List<EmailMessage>> INBOX = new ConcurrentHashMap<>();
    private static final AtomicBoolean FAIL_NEXT = new AtomicBoolean();

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public void send(EmailMessage message) {
        if (FAIL_NEXT.getAndSet(false)) throw new EmailDeliveryException("simulated provider outage", null);
        INBOX.computeIfAbsent(message.to(), ignored -> new CopyOnWriteArrayList<>()).add(message);
    }

    public static List<EmailMessage> inbox(String email) {
        return INBOX.getOrDefault(email, List.of());
    }

    public static String lastCodeFor(String email) {
        List<EmailMessage> messages = inbox(email);
        if (messages.isEmpty()) throw new IllegalStateException("No email was sent to " + email);
        Matcher matcher = CODE.matcher(messages.getLast().text());
        if (!matcher.find()) throw new IllegalStateException("No code in the last email to " + email);
        return matcher.group(1);
    }

    /** Makes the next send fail like an SMTP outage. */
    public static void failNextSend() {
        FAIL_NEXT.set(true);
    }
}
