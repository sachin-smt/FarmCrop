-- Email + password registration with a six-digit email OTP. Additive only: existing tables, accounts,
-- password hashes and refresh tokens are untouched. Portable between MySQL 8 and H2 (MySQL mode).
--
-- A registration waits here until its OTP is verified; only then is the real users row created (ACTIVE,
-- BUYER, email_verified = TRUE). Rows here are never accounts and can be purged once expired or consumed.
-- The existing email_verification_tokens table (V3) is keyed to an existing user and has no attempt or
-- resend tracking, so it is not reused.
CREATE TABLE pending_registrations (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    email                  VARCHAR(254) NOT NULL,
    full_name              VARCHAR(120) NOT NULL,
    username               VARCHAR(40)  NOT NULL,
    phone                  VARCHAR(20)  NOT NULL,
    -- BCrypt hash of the chosen password (never the password itself).
    password_hash          VARCHAR(100) NOT NULL,
    -- HMAC-SHA256 (keyed with EMAIL_OTP_HMAC_SECRET) of the current OTP, hex. Never the OTP itself.
    otp_hmac               VARCHAR(64)  NOT NULL,
    otp_expires_at         DATETIME(6)  NOT NULL,
    -- Wrong codes entered for the current OTP; reset when a new OTP is sent.
    failed_attempts        INT          NOT NULL DEFAULT 0,
    last_sent_at           DATETIME(6)  NOT NULL,
    -- Resend limiting: resends counted within a one-hour window starting at resend_window_started_at.
    resend_window_started_at DATETIME(6) NOT NULL,
    resends_in_window      INT          NOT NULL DEFAULT 0,
    -- Set when the OTP was verified and the account created; the row can then never be used again.
    consumed_at            DATETIME(6)  NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    CONSTRAINT pk_pending_registrations PRIMARY KEY (id),
    CONSTRAINT uk_pending_registrations_email UNIQUE (email),
    CONSTRAINT ck_pending_registrations_attempts CHECK (failed_attempts >= 0 AND resends_in_window >= 0)
);

CREATE INDEX idx_pending_registrations_expires ON pending_registrations (otp_expires_at);
CREATE INDEX idx_pending_registrations_username ON pending_registrations (username);
