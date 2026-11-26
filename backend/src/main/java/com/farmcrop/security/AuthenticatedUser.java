package com.farmcrop.security;

import com.farmcrop.common.enums.UserRole;

import java.util.Set;

/**
 * Principal for authenticated API requests. Built from the database on every request, so role
 * changes (seller approval) and suspensions take effect immediately.
 * Inject in controllers with {@code @AuthenticationPrincipal AuthenticatedUser user}.
 */
public record AuthenticatedUser(Long id, String username, Set<UserRole> roles) {

    public boolean hasRole(UserRole role) {
        return roles.contains(role);
    }
}
