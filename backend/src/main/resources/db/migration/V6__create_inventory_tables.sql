-- Inventory: one row per product. Sellable quantity = on_hand_quantity - reserved_quantity.
-- Stock changes must use conditional atomic updates, e.g.
--   UPDATE inventory SET reserved_quantity = reserved_quantity + :q
--   WHERE product_id = :id AND on_hand_quantity - reserved_quantity >= :q
-- and check the affected row count. The CHECK constraints are the last line of defence:
-- stock can never go negative and reservations can never exceed stock on hand.

CREATE TABLE inventory (
    product_id          BIGINT         NOT NULL,
    on_hand_quantity    DECIMAL(12, 3) NOT NULL DEFAULT 0,
    reserved_quantity   DECIMAL(12, 3) NOT NULL DEFAULT 0,
    low_stock_threshold DECIMAL(12, 3) NULL,
    low_stock_alerted_at DATETIME(6)   NULL,
    updated_at          DATETIME(6)    NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_inventory PRIMARY KEY (product_id),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_inventory_on_hand CHECK (on_hand_quantity >= 0),
    CONSTRAINT ck_inventory_reserved CHECK (reserved_quantity >= 0),
    CONSTRAINT ck_inventory_reserved_le_on_hand CHECK (reserved_quantity <= on_hand_quantity),
    CONSTRAINT ck_inventory_threshold CHECK (low_stock_threshold IS NULL OR low_stock_threshold >= 0)
);

-- Append-only ledger of every stock change, with the resulting balances.
CREATE TABLE inventory_movements (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    product_id       BIGINT         NOT NULL,
    -- INITIAL, INCREASE, DECREASE, CORRECTION, RESERVE, RELEASE, DEDUCT, RESTOCK
    movement_type    VARCHAR(30)    NOT NULL,
    on_hand_delta    DECIMAL(12, 3) NOT NULL,
    reserved_delta   DECIMAL(12, 3) NOT NULL,
    on_hand_after    DECIMAL(12, 3) NOT NULL,
    reserved_after   DECIMAL(12, 3) NOT NULL,
    reason           VARCHAR(500)   NULL,
    -- What caused the movement, e.g. ORDER_ITEM / SELLER_ADJUSTMENT / ADMIN_CORRECTION / RESERVATION.
    reference_type   VARCHAR(30)    NULL,
    reference_id     BIGINT         NULL,
    actor_user_id    BIGINT         NULL,
    created_at       DATETIME(6)    NOT NULL,
    CONSTRAINT pk_inventory_movements PRIMARY KEY (id),
    CONSTRAINT fk_inventory_movements_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_inventory_movements_actor FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT ck_inventory_movements_after CHECK (on_hand_after >= 0 AND reserved_after >= 0)
);

CREATE INDEX idx_inventory_movements_product_time ON inventory_movements (product_id, created_at);
CREATE INDEX idx_inventory_movements_reference ON inventory_movements (reference_type, reference_id);
