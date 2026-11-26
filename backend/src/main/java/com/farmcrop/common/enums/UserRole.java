package com.farmcrop.common.enums;

/**
 * Marketplace roles. Every account is a BUYER; SELLER is granted only through admin approval of a
 * seller application; ADMIN is provisioned only through the bootstrap mechanism.
 */
public enum UserRole {
    BUYER,
    SELLER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
