package com.farmcrop.auth.otp;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailOtpCodecTest {

    private static EmailOtpCodec codec(String rawKey) {
        return new EmailOtpCodec(new EmailOtpProperties(Base64.getEncoder().encodeToString(rawKey.getBytes()), null, null, null, null));
    }

    @Test
    void codesAreSixDigitsAndVary() {
        EmailOtpCodec codec = codec("unit-test-key-that-is-at-least-32-bytes-long");
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String code = codec.generate();
            assertThat(code).matches("[0-9]{6}");
            seen.add(code);
        }
        assertThat(seen.size()).isGreaterThan(190);
    }

    @Test
    void hmacDependsOnKeyEmailAndCodeAndComparesExactly() {
        EmailOtpCodec a = codec("unit-test-key-that-is-at-least-32-bytes-long");
        EmailOtpCodec b = codec("another-unit-test-key-at-least-32-bytes-long");
        String stored = a.hmac("asha@example.test", "123456");
        assertThat(stored).matches("[0-9a-f]{64}");
        assertThat(a.matches("asha@example.test", "123456", stored)).isTrue();
        assertThat(a.matches("asha@example.test", "123457", stored)).isFalse();
        assertThat(a.matches("ravi@example.test", "123456", stored)).isFalse();
        assertThat(b.matches("asha@example.test", "123456", stored)).isFalse();
        assertThat(a.matches("asha@example.test", null, stored)).isFalse();
    }

    @Test
    void aMissingOrShortKeyDisablesTheCodec() {
        EmailOtpCodec missing = new EmailOtpCodec(new EmailOtpProperties("", null, null, null, null));
        assertThat(missing.isConfigured()).isFalse();
        assertThatThrownBy(() -> missing.hmac("a@example.test", "123456")).isInstanceOf(IllegalStateException.class);
        assertThat(codec("too-short").isConfigured()).isFalse();
        assertThat(new EmailOtpCodec(new EmailOtpProperties("not base64 !!", null, null, null, null)).isConfigured()).isFalse();
    }

    @Test
    void defaultsMatchThePolicy() {
        EmailOtpProperties defaults = new EmailOtpProperties(null, null, null, null, null);
        assertThat(defaults.ttl()).hasMinutes(10);
        assertThat(defaults.resendCooldown()).hasSeconds(60);
        assertThat(defaults.maxAttempts()).isEqualTo(5);
        assertThat(defaults.maxResendsPerHour()).isEqualTo(3);
        assertThat(defaults.toString()).doesNotContain("hmacSecret=" + "x");
    }
}
