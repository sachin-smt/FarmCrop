-- Persistent in-app notifications (one row per recipient) and per-user channel preferences.
-- Email/SMS delivery is optional and provider-configurable; in-app rows are the source of truth.

CREATE TABLE notifications (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    recipient_user_id BIGINT        NOT NULL,
    -- e.g. SELLER_APPLICATION_SUBMITTED, SELLER_APPLICATION_APPROVED, ORDER_PLACED, PAYMENT_FAILED, LOW_STOCK
    notification_type VARCHAR(50)   NOT NULL,
    title             VARCHAR(150)  NOT NULL,
    message           VARCHAR(1000) NOT NULL,
    reference_type    VARCHAR(40)   NULL,
    reference_id      BIGINT        NULL,
    -- In-app route to open, e.g. /admin/applications/12 (relative path only, never an external URL).
    link_path         VARCHAR(255)  NULL,
    is_read           BOOLEAN       NOT NULL DEFAULT FALSE,
    read_at           DATETIME(6)   NULL,
    created_at        DATETIME(6)   NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Unread badge counts and newest-first lists per user.
CREATE INDEX idx_notifications_recipient_read_time ON notifications (recipient_user_id, is_read, created_at);
CREATE INDEX idx_notifications_reference ON notifications (reference_type, reference_id);

CREATE TABLE notification_preferences (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    user_id           BIGINT      NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    -- IN_APP, EMAIL, SMS
    channel           VARCHAR(20) NOT NULL,
    enabled           BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at        DATETIME(6) NOT NULL,
    CONSTRAINT pk_notification_preferences PRIMARY KEY (id),
    CONSTRAINT uk_notification_preferences_user_type_channel UNIQUE (user_id, notification_type, channel),
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
