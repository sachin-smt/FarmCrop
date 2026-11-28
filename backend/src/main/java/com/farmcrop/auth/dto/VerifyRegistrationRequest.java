package com.farmcrop.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Completes an email registration. The password chosen at registration is required too, so only the person
 * who set it (and who can read the inbox) can activate the account.
 */
public record VerifyRegistrationRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 254, message = "Email is too long.")
        String email,

        @NotBlank(message = "Enter the 6-digit code.")
        @Pattern(regexp = "^[0-9]{6}$", message = "Enter the 6-digit code.")
        String otp,

        @NotBlank(message = "Password is required.")
        @Size(max = 128, message = "Password is too long.")
        String password,

        /** Optional: a different username, e.g. after the chosen one was taken. Validated like registration. */
        @Size(max = 30, message = "Username must be 3-30 characters.")
        String username
) {

    @Override
    public String toString() {
        return "VerifyRegistrationRequest[email=" + email + ", otp=***, password=***, username=" + username + "]";
    }
}
