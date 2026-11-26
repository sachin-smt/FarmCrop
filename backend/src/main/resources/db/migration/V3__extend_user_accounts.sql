-- Account lifecycle extensions (additive; V1 tables are not recreated).
-- Portable between MySQL 8 and H2 (MySQL mode); see .claude/skills/farmcrop-migrations.

ALTER TABLE users ADD COLUMN profile_image_ref VARCHAR(255) NULL;
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN phone_verified BOOLEAN NOT NULL DEFAULT FALSE;
-- Why the account was suspended/reactivated (latest decision; full history lives in audit_logs).
ALTER TABLE users ADD COLUMN status_reason VARCHAR(500) NULL;
ALTER TABLE users ADD COLUMN status_changed_at DATETIME(6) NULL;

-- Single-use password reset tokens. Only a SHA-256 hash of the token is stored.
CREATE TABLE password_reset_tokens (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    used_at     DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_tokens_expires ON password_reset_tokens (expires_at);

-- Single-use email verification tokens. Only a SHA-256 hash of the token is stored.
CREATE TABLE email_verification_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    email       VARCHAR(254) NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    used_at     DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_email_verification_tokens PRIMARY KEY (id),
    CONSTRAINT uk_email_verification_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_email_verification_tokens_expires ON email_verification_tokens (expires_at);
