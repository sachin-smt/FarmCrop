package com.farmcrop.auth.otp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Generates six-digit codes with {@link SecureRandom} and stores them only as HMAC-SHA256 keyed with a server
 * secret (EMAIL_OTP_HMAC_SECRET). A plain hash would be useless for a 10^6 search space; without the key,
 * a leaked database row reveals nothing. The email is mixed in, so a code value is bound to its address.
 */
@Component
@EnableConfigurationProperties(EmailOtpProperties.class)
public class EmailOtpCodec {

    private static final Logger log = LoggerFactory.getLogger(EmailOtpCodec.class);
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public EmailOtpCodec(EmailOtpProperties properties) {
        this.key = parseKey(properties.hmacSecret());
        if (key == null) {
            log.warn("EMAIL_OTP_HMAC_SECRET is missing or shorter than {} bytes (base64). Email registration will be unavailable.", MIN_KEY_BYTES);
        }
    }

    public boolean isConfigured() {
        return key != null;
    }

    /** A uniformly random code 000000-999999. */
    public String generate() {
        return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }

    public String hmac(String email, String code) {
        if (key == null) throw new IllegalStateException("EMAIL_OTP_HMAC_SECRET is not configured.");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal(("email-otp:v1:" + email + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }

    /** Constant-time comparison of a submitted code against the stored HMAC. */
    public boolean matches(String email, String code, String storedHmac) {
        if (code == null || storedHmac == null) return false;
        return MessageDigest.isEqual(hmac(email, code).getBytes(StandardCharsets.US_ASCII),
                storedHmac.getBytes(StandardCharsets.US_ASCII));
    }

    private static SecretKeySpec parseKey(String secret) {
        if (secret == null || secret.isBlank()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(secret);
            return bytes.length >= MIN_KEY_BYTES ? new SecretKeySpec(bytes, "HmacSHA256") : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
