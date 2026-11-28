package com.farmcrop.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An external sign-in identity (e.g. a Google account) linked to one FarmCrop user. */
@Entity
@Table(name = "user_auth_identities")
public class UserAuthIdentity {

    public enum Provider { GOOGLE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private Provider provider;

    /** The provider's stable account id (Google "sub"), never the email, which can change. */
    @Column(name = "provider_subject", nullable = false, length = 128)
    private String providerSubject;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected UserAuthIdentity() {
    }

    public UserAuthIdentity(Long userId, Provider provider, String providerSubject, Instant now) {
        this.userId = userId;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.createdAt = now;
        this.lastUsedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Provider getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }
}
