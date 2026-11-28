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

    /** Indian mobile number in E.164 form, as returned by Firebase phone sign-in: +91 then 10 digits starting 6-9. */
    public static final String INDIAN_MOBILE_E164 = "^\\+91[6-9][0-9]{9}$";

    private static final java.util.regex.Pattern INDIAN_MOBILE_ANY_FORM =
            java.util.regex.Pattern.compile("^(?:\\+?91)?([6-9][0-9]{9})$");

    public static String normalizePhone(String phone) {
        if (phone == null) return null;
        String cleaned = phone.replaceAll("[\\s-]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * Every stored form of the same Indian mobile number ({@code +91XXXXXXXXXX}, {@code 91XXXXXXXXXX},
     * {@code XXXXXXXXXX}), because registration historically accepted all of them. Non-Indian numbers
     * yield just the normalized input. Used to prevent one number from belonging to two accounts.
     */
    public static java.util.List<String> equivalentPhones(String phone) {
        String normalized = normalizePhone(phone);
        if (normalized == null) return java.util.List.of();
        java.util.regex.Matcher matcher = INDIAN_MOBILE_ANY_FORM.matcher(normalized);
        if (!matcher.matches()) return java.util.List.of(normalized);
        String national = matcher.group(1);
        return java.util.List.of("+91" + national, "91" + national, national);
    }
}
