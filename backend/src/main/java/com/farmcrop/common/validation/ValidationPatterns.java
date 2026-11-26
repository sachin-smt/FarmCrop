package com.farmcrop.common.validation;

import java.util.Set;

/** Shared input rules so registration, profile updates and admin bootstrap validate identically. */
public final class ValidationPatterns {

    /** 3-30 chars, lower-case letters/digits/dot/underscore, starting and ending with a letter or digit. */
    public static final String USERNAME = "^[a-z0-9](?:[a-z0-9._]{1,28})[a-z0-9]$";

    /** Optional leading +, then 10-15 digits (spaces and dashes are stripped before checking). */
    public static final String PHONE = "^\\+?[0-9]{10,15}$";

    /** 8-72 characters (BCrypt limit), at least one letter and one digit. */
    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";

    public static final Set<String> LANGUAGES =
            Set.of("en", "hi", "mr", "bn", "te", "raj", "kn", "mai", "gu", "ta", "ml", "pa", "as");

    /** Names a member of the public may not register, to prevent staff impersonation. */
    public static final Set<String> RESERVED_USERNAMES =
            Set.of("admin", "administrator", "root", "system", "support", "farmcrop", "help", "security", "moderator");

    private ValidationPatterns() {
    }

    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        String cleaned = phone.replaceAll("[\\s-]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }
}
