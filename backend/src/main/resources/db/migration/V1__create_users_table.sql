-- FarmCrop user management schema.
-- SQL is kept portable between MySQL 8 (InnoDB, utf8mb4 defaults) and H2 in MySQL mode,
-- so the same migrations run in automated tests.
-- Timestamps are stored in UTC.

CREATE TABLE roles (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    role_name VARCHAR(30) NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_role_name UNIQUE (role_name)
);

INSERT INTO roles (role_name) VALUES ('BUYER');
INSERT INTO roles (role_name) VALUES ('SELLER');
INSERT INTO roles (role_name) VALUES ('ADMIN');

CREATE TABLE users (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    full_name          VARCHAR(120) NOT NULL,
    username           VARCHAR(40)  NOT NULL,
    email              VARCHAR(254) NOT NULL,
    phone              VARCHAR(20)  NULL,
    password_hash      VARCHAR(100) NOT NULL,
    account_status     VARCHAR(20)  NOT NULL,
    preferred_language VARCHAR(10)  NOT NULL DEFAULT 'en',
    last_login_at      DATETIME(6)  NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone)
);

CREATE INDEX idx_users_account_status ON users (account_status);
CREATE INDEX idx_users_created_at ON users (created_at);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE INDEX idx_user_roles_role ON user_roles (role_id);

CREATE TABLE user_addresses (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    recipient_name VARCHAR(120) NOT NULL,
    phone          VARCHAR(20)  NOT NULL,
    address_line   VARCHAR(255) NOT NULL,
    city           VARCHAR(100) NOT NULL,
    state          VARCHAR(100) NOT NULL,
    postal_code    VARCHAR(12)  NOT NULL,
    country        VARCHAR(60)  NOT NULL DEFAULT 'India',
    address_type   VARCHAR(20)  NOT NULL,
    is_default     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    CONSTRAINT pk_user_addresses PRIMARY KEY (id),
    CONSTRAINT fk_user_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_addresses_user ON user_addresses (user_id);
