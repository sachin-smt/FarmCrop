-- Payments (Razorpay first). No card numbers, CVV, UPI PINs or bank credentials are ever stored.
-- Webhook idempotency: every gateway event id is stored once (unique), so retries are recognised.

CREATE TABLE payments (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    order_id            BIGINT         NOT NULL,
    buyer_id            BIGINT         NOT NULL,
    gateway             VARCHAR(20)    NOT NULL DEFAULT 'RAZORPAY',
    gateway_order_id    VARCHAR(64)    NOT NULL,
    gateway_payment_id  VARCHAR(64)    NULL,
    amount              DECIMAL(12, 2) NOT NULL,
    currency            VARCHAR(3)     NOT NULL DEFAULT 'INR',
    -- CREATED, PENDING, SUCCESS, FAILED, REFUND_PENDING, REFUNDED
    status              VARCHAR(20)    NOT NULL,
    -- Non-sensitive method label reported by the gateway, e.g. upi / card / netbanking.
    method              VARCHAR(30)    NULL,
    signature_verified  BOOLEAN        NOT NULL DEFAULT FALSE,
    failure_code        VARCHAR(80)    NULL,
    failure_reason      VARCHAR(500)   NULL,
    attempt_number      INT            NOT NULL DEFAULT 1,
    captured_at         DATETIME(6)    NULL,
    failed_at           DATETIME(6)    NULL,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT uk_payments_gateway_order UNIQUE (gateway_order_id),
    CONSTRAINT uk_payments_gateway_payment UNIQUE (gateway_payment_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_payments_buyer FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_attempt CHECK (attempt_number >= 1)
);

CREATE INDEX idx_payments_order_status ON payments (order_id, status);
CREATE INDEX idx_payments_status_created ON payments (status, created_at);

-- Every webhook / verification event received from the gateway. The raw payload is not stored
-- (it can contain buyer contact details); only its SHA-256 hash for tamper evidence.
CREATE TABLE payment_events (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    payment_id        BIGINT        NULL,
    gateway           VARCHAR(20)   NOT NULL DEFAULT 'RAZORPAY',
    gateway_event_id  VARCHAR(100)  NOT NULL,
    event_type        VARCHAR(60)   NOT NULL,
    signature_valid   BOOLEAN       NOT NULL,
    -- RECEIVED, PROCESSED, IGNORED, FAILED
    processing_status VARCHAR(20)   NOT NULL,
    payload_sha256    VARCHAR(64)   NOT NULL,
    error_message     VARCHAR(500)  NULL,
    received_at       DATETIME(6)   NOT NULL,
    processed_at      DATETIME(6)   NULL,
    CONSTRAINT pk_payment_events PRIMARY KEY (id),
    CONSTRAINT uk_payment_events_gateway_event UNIQUE (gateway, gateway_event_id),
    CONSTRAINT fk_payment_events_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);

CREATE INDEX idx_payment_events_payment ON payment_events (payment_id, received_at);
CREATE INDEX idx_payment_events_status ON payment_events (processing_status, received_at);

CREATE TABLE refunds (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    payment_id        BIGINT         NOT NULL,
    order_id          BIGINT         NOT NULL,
    seller_order_id   BIGINT         NULL,
    gateway_refund_id VARCHAR(64)    NULL,
    amount            DECIMAL(12, 2) NOT NULL,
    -- PENDING, PROCESSED, FAILED
    status            VARCHAR(20)    NOT NULL,
    reason            VARCHAR(500)   NULL,
    requested_by      BIGINT         NULL,
    processed_at      DATETIME(6)    NULL,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    CONSTRAINT pk_refunds PRIMARY KEY (id),
    CONSTRAINT uk_refunds_gateway_refund UNIQUE (gateway_refund_id),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_refunds_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_refunds_seller_order FOREIGN KEY (seller_order_id) REFERENCES seller_orders (id),
    CONSTRAINT fk_refunds_requested_by FOREIGN KEY (requested_by) REFERENCES users (id),
    CONSTRAINT ck_refunds_amount CHECK (amount > 0)
);

CREATE INDEX idx_refunds_order ON refunds (order_id, created_at);
CREATE INDEX idx_refunds_status ON refunds (status, created_at);
