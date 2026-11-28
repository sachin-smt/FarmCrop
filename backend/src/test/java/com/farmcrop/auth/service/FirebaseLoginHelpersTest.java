package com.farmcrop.auth.service;

import com.farmcrop.common.validation.ValidationPatterns;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseLoginHelpersTest {

    @Test
    void usernameBaseIsDerivedSafelyFromTheEmail() {
        assertThat(FirebaseLoginService.usernameBase("Asha.K+farm@gmail.com")).isEqualTo("asha.k");
        assertThat(FirebaseLoginService.usernameBase("._ravi_@example.com")).isEqualTo("ravi");
        assertThat(FirebaseLoginService.usernameBase("verylongemailaddresspart1234@x.in")).hasSizeLessThanOrEqualTo(20);
        assertThat(FirebaseLoginService.usernameBase("admin@example.com")).isEqualTo("buyer");
        assertThat(FirebaseLoginService.usernameBase("é@example.com")).isEqualTo("buyer");
    }

    @Test
    void indianNumbersHaveThreeEquivalentStoredForms() {
        assertThat(ValidationPatterns.equivalentPhones("+91 98765-43210"))
                .containsExactly("+919876543210", "919876543210", "9876543210");
        assertThat(ValidationPatterns.equivalentPhones("9876543210"))
                .containsExactly("+919876543210", "919876543210", "9876543210");
        assertThat(ValidationPatterns.equivalentPhones("+14155550123")).containsExactly("+14155550123");
        assertThat(ValidationPatterns.equivalentPhones(null)).isEmpty();
        assertThat("+919876543210").matches(ValidationPatterns.INDIAN_MOBILE_E164);
        assertThat("+915876543210").doesNotMatch(ValidationPatterns.INDIAN_MOBILE_E164);
    }
}
