package com.farmcrop.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A registration waiting for its email OTP (V15). Not an account: the users row is created only after
 * successful verification. Stores the BCrypt password hash and an HMAC of the OTP, never either secret.
 */
@Entity
@Table(name = "pending_registrations")
public class PendingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "username", nullable = false, length = 40)
    private String username;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "otp_hmac", nullable = false, length = 64)
    private String otpHmac;

    @Column(name = "otp_expires_at", nullable = false)
    private Instant otpExpiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "last_sent_at", nullable = false)
    private Instant lastSentAt;

    @Column(name = "resend_window_started_at", nullable = false)
    private Instant resendWindowStartedAt;

    @Column(name = "resends_in_window", nullable = false)
    private int resendsInWindow;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PendingRegistration() {
    }

    public PendingRegistration(String email, Instant now) {
        this.email = email;
        this.createdAt = now;
        this.updatedAt = now;
        this.resendWindowStartedAt = now;
    }

    /** Replaces the submitted details (a repeated registration for the same email). */
    public void setDetails(String fullName, String username, String phone, String passwordHash) {
        this.fullName = fullName;
        this.username = username;
        this.phone = phone;
        this.passwordHash = passwordHash;
    }

    /** Installs a new OTP: the previous one stops working and the attempt counter restarts. */
    public void issueOtp(String otpHmac, Instant expiresAt, Instant now) {
        this.otpHmac = otpHmac;
        this.otpExpiresAt = expiresAt;
        this.failedAttempts = 0;
        this.lastSentAt = now;
        this.consumedAt = null;
        this.updatedAt = now;
    }

    public void recordResend(Instant now, java.time.Duration window) {
        if (resendWindowStartedAt == null || !now.isBefore(resendWindowStartedAt.plus(window))) {
            resendWindowStartedAt = now;
            resendsInWindow = 0;
        }
        resendsInWindow++;
    }

    public int resendsInWindow(Instant now, java.time.Duration window) {
        return resendWindowStartedAt != null && now.isBefore(resendWindowStartedAt.plus(window)) ? resendsInWindow : 0;
    }

    /** The owner (proven by code + password) picked another username before the account was created. */
    public void changeUsername(String username, Instant now) {
        this.username = username;
        this.updatedAt = now;
    }

    /** The email for the latest code could not be sent: give back its cooldown and (for a resend) its quota. */
    public void releaseFailedDelivery(java.time.Duration cooldown, boolean wasResend) {
        lastSentAt = lastSentAt.minus(cooldown);
        if (wasResend && resendsInWindow > 0) resendsInWindow--;
    }

    public void recordFailedAttempt(Instant now) {
        failedAttempts++;
        updatedAt = now;
    }

    public void consume(Instant now) {
        consumedAt = now;
        updatedAt = now;
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getUsername() { return username; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public String getOtpHmac() { return otpHmac; }
    public Instant getOtpExpiresAt() { return otpExpiresAt; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getLastSentAt() { return lastSentAt; }
    public Instant getConsumedAt() { return consumedAt; }
}
