package com.farmcrop.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Google or phone sign-in: the Firebase ID token the browser obtained after the user completed the
 * Google popup or entered the SMS code. Everything else (user, phone, email, role) is derived from the
 * verified token on the server, never taken from the request.
 */
public record FirebaseSignInRequest(
        @NotBlank(message = "Sign-in token is required.")
        @Size(max = 8192, message = "Sign-in token is too long.")
        String idToken
) {

    @Override
    public String toString() {
        return "FirebaseSignInRequest[idToken=***]";
    }
}
