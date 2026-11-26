-- Server-side refresh token store (only SHA-256 hashes are stored, never the raw token).
-- Tokens rotate on every use; presenting an already-rotated token revokes the whole family
-- (refresh-token reuse detection).

CREATE TABLE refresh_tokens (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    token_hash   VARCHAR(64) NOT NULL,
    family_id    VARCHAR(36) NOT NULL,
    expires_at   DATETIME(6) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    revoked_at   DATETIME(6) NULL,
    revoke_reason VARCHAR(30) NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
