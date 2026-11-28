-- Shared, persistent rate limiting (replaces per-instance in-memory counters). Additive only.
-- Portable between MySQL 8 and H2 (MySQL mode). Keys are SHA-256 hashes of e.g. "register:<ip>" or
-- "email-send-cooldown:<email>", so no raw IP addresses or emails are stored here.

-- One row per limited key; locked (SELECT ... FOR UPDATE) while a request is counted, so concurrent requests
-- on different backend instances cannot exceed a limit.
CREATE TABLE rate_limit_keys (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    key_hash    VARCHAR(64) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_rate_limit_keys PRIMARY KEY (id),
    CONSTRAINT uk_rate_limit_keys_key_hash UNIQUE (key_hash)
);

CREATE INDEX idx_rate_limit_keys_created ON rate_limit_keys (created_at);

-- One row per counted request (exact sliding windows). Rows are purged once expired.
CREATE TABLE rate_limit_hits (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    key_hash    VARCHAR(64) NOT NULL,
    hit_at      DATETIME(6) NOT NULL,
    expires_at  DATETIME(6) NOT NULL,
    CONSTRAINT pk_rate_limit_hits PRIMARY KEY (id)
);

CREATE INDEX idx_rate_limit_hits_key_time ON rate_limit_hits (key_hash, hit_at);
CREATE INDEX idx_rate_limit_hits_expires ON rate_limit_hits (expires_at);
