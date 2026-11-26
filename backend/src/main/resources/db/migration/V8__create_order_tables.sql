-- Orders. One buyer checkout = one `orders` row. Items are grouped per seller into `seller_orders`
-- so every seller fulfils (accepts, ships, delivers) their part independently.
-- Order rows keep immutable snapshots (prices, product names, delivery address) so history stays
-- accurate when products or addresses change later. Foreign keys use the default RESTRICT
-- behaviour: order history can never be removed by deleting a user or product.

CREATE TABLE orders (
    id                       BIGINT         NOT NULL AUTO_INCREMENT,
    order_number             VARCHAR(30)    NOT NULL,
    buyer_id                 BIGINT         NOT NULL,
    -- PENDING, CONFIRMED, PROCESSING, SHIPPED, OUT_FOR_DELIVERY, DELIVERED, CANCELLED, REFUND_PENDING, REFUNDED
    status                   VARCHAR(30)    NOT NULL,
    -- CREATED, PENDING, SUCCESS, FAILED, REFUND_PENDING, REFUNDED
    payment_status           VARCHAR(30)    NOT NULL,
    currency                 VARCHAR(3)     NOT NULL DEFAULT 'INR',
    subtotal                 DECIMAL(12, 2) NOT NULL,
    shipping_total           DECIMAL(12, 2) NOT NULL DEFAULT 0,
    discount_total           DECIMAL(12, 2) NOT NULL DEFAULT 0,
    grand_total              DECIMAL(12, 2) NOT NULL,
    -- Delivery address snapshot taken at checkout.
    delivery_recipient_name  VARCHAR(120)   NOT NULL,
    delivery_phone           VARCHAR(20)    NOT NULL,
    delivery_address_line    VARCHAR(255)   NOT NULL,
    delivery_city            VARCHAR(100)   NOT NULL,
    delivery_state           VARCHAR(100)   NOT NULL,
    delivery_postal_code     VARCHAR(12)    NOT NULL,
    delivery_country         VARCHAR(60)    NOT NULL,
    -- Client-generated key per checkout attempt; a repeated submission returns the existing order.
    idempotency_key          VARCHAR(80)    NULL,
    placed_at                DATETIME(6)    NOT NULL,
    cancelled_at             DATETIME(6)    NULL,
    cancel_reason            VARCHAR(500)   NULL,
    created_at               DATETIME(6)    NOT NULL,
    updated_at               DATETIME(6)    NOT NULL,
    version                  BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_orders PRIMARY KEY (id),
    CONSTRAINT uk_orders_number UNIQUE (order_number),
    CONSTRAINT uk_orders_buyer_idempotency UNIQUE (buyer_id, idempotency_key),
    CONSTRAINT fk_orders_buyer FOREIGN KEY (buyer_id) REFERENCES users (id),
    CONSTRAINT ck_orders_amounts CHECK (subtotal >= 0 AND shipping_total >= 0 AND discount_total >= 0 AND grand_total >= 0),
    CONSTRAINT ck_orders_total CHECK (grand_total = subtotal + shipping_total - discount_total)
);

CREATE INDEX idx_orders_buyer_placed ON orders (buyer_id, placed_at);
CREATE INDEX idx_orders_status_placed ON orders (status, placed_at);
CREATE INDEX idx_orders_payment_status ON orders (payment_status);

-- Per-seller fulfilment group within an order.
CREATE TABLE seller_orders (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    order_id            BIGINT         NOT NULL,
    seller_id           BIGINT         NOT NULL,
    seller_order_number VARCHAR(40)    NOT NULL,
    -- Same status vocabulary as orders.status, tracked independently per seller.
    status              VARCHAR(30)    NOT NULL,
    subtotal            DECIMAL(12, 2) NOT NULL,
    shipping_amount     DECIMAL(12, 2) NOT NULL DEFAULT 0,
    total               DECIMAL(12, 2) NOT NULL,
    tracking_carrier    VARCHAR(80)    NULL,
    tracking_number     VARCHAR(80)    NULL,
    accepted_at         DATETIME(6)    NULL,
    shipped_at          DATETIME(6)    NULL,
    delivered_at        DATETIME(6)    NULL,
    cancelled_at        DATETIME(6)    NULL,
    cancel_reason       VARCHAR(500)   NULL,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_seller_orders PRIMARY KEY (id),
    CONSTRAINT uk_seller_orders_number UNIQUE (seller_order_number),
    CONSTRAINT uk_seller_orders_order_seller UNIQUE (order_id, seller_id),
    CONSTRAINT fk_seller_orders_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_seller_orders_seller FOREIGN KEY (seller_id) REFERENCES seller_profiles (id),
    CONSTRAINT ck_seller_orders_amounts CHECK (subtotal >= 0 AND shipping_amount >= 0 AND total = subtotal + shipping_amount)
);

CREATE INDEX idx_seller_orders_seller_status ON seller_orders (seller_id, status, created_at);

CREATE TABLE order_items (
    id                      BIGINT         NOT NULL AUTO_INCREMENT,
    order_id                BIGINT         NOT NULL,
    seller_order_id         BIGINT         NOT NULL,
    product_id              BIGINT         NOT NULL,
    -- Snapshots: never recomputed from the live product.
    product_name_snapshot   VARCHAR(150)   NOT NULL,
    category_name_snapshot  VARCHAR(80)    NULL,
    unit_snapshot           VARCHAR(20)    NOT NULL,
    image_ref_snapshot      VARCHAR(255)   NULL,
    unit_price              DECIMAL(12, 2) NOT NULL,
    quantity                DECIMAL(12, 3) NOT NULL,
    line_total              DECIMAL(12, 2) NOT NULL,
    created_at              DATETIME(6)    NOT NULL,
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT uk_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_seller_order FOREIGN KEY (seller_order_id) REFERENCES seller_orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_order_items_values CHECK (unit_price > 0 AND quantity > 0 AND line_total > 0)
);

CREATE INDEX idx_order_items_seller_order ON order_items (seller_order_id);
CREATE INDEX idx_order_items_product ON order_items (product_id);

-- Append-only status timeline for orders and seller fulfilment groups.
CREATE TABLE order_status_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    order_id        BIGINT       NOT NULL,
    seller_order_id BIGINT       NULL,
    from_status     VARCHAR(30)  NULL,
    to_status       VARCHAR(30)  NOT NULL,
    actor_user_id   BIGINT       NULL,
    -- BUYER, SELLER, ADMIN, SYSTEM (e.g. payment webhook or reservation expiry)
    actor_type      VARCHAR(20)  NOT NULL,
    note            VARCHAR(500) NULL,
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT pk_order_status_history PRIMARY KEY (id),
    CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_status_history_seller_order FOREIGN KEY (seller_order_id) REFERENCES seller_orders (id),
    CONSTRAINT fk_order_status_history_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
);

CREATE INDEX idx_order_status_history_order_time ON order_status_history (order_id, created_at);

-- Stock held for an unpaid order item. Released on payment failure/cancellation/expiry,
-- consumed (stock deducted) exactly once on confirmed payment.
CREATE TABLE inventory_reservations (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    order_item_id BIGINT         NOT NULL,
    product_id    BIGINT         NOT NULL,
    quantity      DECIMAL(12, 3) NOT NULL,
    -- ACTIVE, CONSUMED, RELEASED, EXPIRED
    status        VARCHAR(20)    NOT NULL,
    expires_at    DATETIME(6)    NOT NULL,
    consumed_at   DATETIME(6)    NULL,
    released_at   DATETIME(6)    NULL,
    created_at    DATETIME(6)    NOT NULL,
    version       BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_inventory_reservations PRIMARY KEY (id),
    CONSTRAINT uk_inventory_reservations_order_item UNIQUE (order_item_id),
    CONSTRAINT fk_inventory_reservations_order_item FOREIGN KEY (order_item_id) REFERENCES order_items (id),
    CONSTRAINT fk_inventory_reservations_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_inventory_reservations_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_inventory_reservations_status_expiry ON inventory_reservations (status, expires_at);
CREATE INDEX idx_inventory_reservations_product_status ON inventory_reservations (product_id, status);
