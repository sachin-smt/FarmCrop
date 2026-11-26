package com.farmcrop.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Matches the existing frontend login form: one field accepts a username or an email. */
public record LoginRequest(
        @NotBlank(message = "Username or email is required.")
        @Size(max = 254, message = "Username or email is too long.")
        String usernameOrEmail,

        @NotBlank(message = "Password is required.")
        @Size(max = 128, message = "Password is too long.")
        String password
) {

    @Override
    public String toString() {
        return "LoginRequest[usernameOrEmail=" + usernameOrEmail + ", password=***]";
    }
}
