package com.farmcrop.user.dto;

import com.farmcrop.common.enums.AccountStatus;
import com.farmcrop.common.enums.UserRole;
import com.farmcrop.user.entity.User;

import java.time.Instant;
import java.util.List;

/**
 * The signed-in user's own profile. Never includes the password hash or internal versioning.
 * {@code email} is null for accounts created by phone sign-in.
 */
public record UserProfileResponse(
        Long id,
        String fullName,
        String username,
        String email,
        String phone,
        boolean emailVerified,
        boolean phoneVerified,
        List<UserRole> roles,
        AccountStatus accountStatus,
        String preferredLanguage,
        Instant createdAt
) {

    public static UserProfileResponse from(User user) {
        List<UserRole> roles = user.getRoles().stream().map(role -> role.getRoleName()).sorted().toList();
        return new UserProfileResponse(user.getId(), user.getFullName(), user.getUsername(), user.getEmail(),
                user.getPhone(), user.isEmailVerified(), user.isPhoneVerified(), roles, user.getAccountStatus(),
                user.getPreferredLanguage(), user.getCreatedAt());
    }
}
