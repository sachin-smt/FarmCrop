package com.farmcrop.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Self-service profile edit. Username, email, roles and status are not editable here. */
public record UpdateProfileRequest(
        @NotBlank(message = "Full name is required.")
        @Size(min = 2, max = 120, message = "Full name must be 2-120 characters.")
        String fullName,

        @Size(max = 20, message = "Phone number is too long.")
        String phone,

        @Size(max = 10, message = "Unsupported language.")
        String preferredLanguage
) {
}
