package com.farmcrop.auth.dto;

import com.farmcrop.common.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public registration. There is intentionally no role field: every new account is a BUYER, and
 * unknown JSON properties (e.g. "roles") are ignored.
 */
public record RegisterRequest(
        @NotBlank(message = "Full name is required.")
        @Size(min = 2, max = 120, message = "Full name must be 2-120 characters.")
        String fullName,

        @NotBlank(message = "Username is required.")
        @Size(min = 3, max = 30, message = "Username must be 3-30 characters.")
        String username,

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 254, message = "Email is too long.")
        String email,

        @NotBlank(message = "Phone number is required.")
        @Size(max = 20, message = "Phone number is too long.")
        String phone,

        @NotBlank(message = "Password is required.")
        @Pattern(regexp = ValidationPatterns.PASSWORD,
                message = "Password must be 8-72 characters and include at least one letter and one number.")
        String password,

        @NotBlank(message = "Please confirm your password.")
        String confirmPassword
) {

    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", email=" + email + ", password=***, confirmPassword=***]";
    }
}
