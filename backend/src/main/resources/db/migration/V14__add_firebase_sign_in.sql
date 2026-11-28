-- Google and phone (OTP) sign-in through Firebase Authentication. Additive and non-destructive.
-- Portable between MySQL 8 and H2 (MySQL mode); see .claude/skills/farmcrop-migrations.

-- Accounts created by phone OTP have no email address. Widening NOT NULL -> NULL keeps every existing row and
-- the uk_users_email unique constraint (MySQL and H2 both allow several NULLs in a unique column).
ALTER TABLE users MODIFY email VARCHAR(254) NULL;

-- External identities linked to a FarmCrop account, e.g. provider GOOGLE + Google account id ("sub").
-- One identity per provider per user; an external identity can belong to only one user.
CREATE TABLE user_auth_identities (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    provider         VARCHAR(30)  NOT NULL,
    provider_subject VARCHAR(128) NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    last_used_at     DATETIME(6)  NULL,
    CONSTRAINT pk_user_auth_identities PRIMARY KEY (id),
    CONSTRAINT uk_user_auth_identities_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uk_user_auth_identities_user_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_user_auth_identities_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_auth_identities_user ON user_auth_identities (user_id);

-- Firebase ID tokens already exchanged for a FarmCrop session (single use). Only a SHA-256 hash is stored;
-- rows can be purged once the token itself has expired.
CREATE TABLE consumed_identity_tokens (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_consumed_identity_tokens PRIMARY KEY (id),
    CONSTRAINT uk_consumed_identity_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_consumed_identity_tokens_expires ON consumed_identity_tokens (expires_at);
