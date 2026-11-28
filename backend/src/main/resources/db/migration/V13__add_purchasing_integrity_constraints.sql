-- Strengthen cross-row purchasing relationships without replacing existing keys or tables.
-- seller_id is copied onto each order item so the database can verify both its seller order
-- and product belong to the same seller. Existing order-item values are preserved.

ALTER TABLE order_items ADD COLUMN seller_id BIGINT NULL;

UPDATE order_items
SET seller_id = (
    SELECT seller_orders.seller_id
    FROM seller_orders
    WHERE seller_orders.id = order_items.seller_order_id
      AND seller_orders.order_id = order_items.order_id
);

ALTER TABLE order_items
    ADD CONSTRAINT ck_order_items_seller_required CHECK (seller_id IS NOT NULL);

ALTER TABLE products
    ADD CONSTRAINT uk_products_id_seller UNIQUE (id, seller_id);

ALTER TABLE seller_orders
    ADD CONSTRAINT uk_seller_orders_id_order UNIQUE (id, order_id);

ALTER TABLE seller_orders
    ADD CONSTRAINT uk_seller_orders_id_order_seller UNIQUE (id, order_id, seller_id);

ALTER TABLE orders
    ADD CONSTRAINT uk_orders_id_buyer UNIQUE (id, buyer_id);

ALTER TABLE payments
    ADD CONSTRAINT uk_payments_id_order UNIQUE (id, order_id);

ALTER TABLE order_items
    ADD CONSTRAINT uk_order_items_id_product UNIQUE (id, product_id);

ALTER TABLE payments
    ADD CONSTRAINT uk_payments_order_attempt UNIQUE (order_id, attempt_number);

CREATE INDEX idx_order_items_seller_order_identity
    ON order_items (seller_order_id, order_id, seller_id);

CREATE INDEX idx_order_items_product_seller
    ON order_items (product_id, seller_id);

CREATE INDEX idx_payments_order_buyer
    ON payments (order_id, buyer_id);

CREATE INDEX idx_refunds_payment_order
    ON refunds (payment_id, order_id);

CREATE INDEX idx_refunds_seller_order_order
    ON refunds (seller_order_id, order_id);

CREATE INDEX idx_inventory_reservations_item_product
    ON inventory_reservations (order_item_id, product_id);

ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_seller_order_identity
        FOREIGN KEY (seller_order_id, order_id, seller_id)
        REFERENCES seller_orders (id, order_id, seller_id);

ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_product_seller
        FOREIGN KEY (product_id, seller_id)
        REFERENCES products (id, seller_id);

ALTER TABLE payments
    ADD CONSTRAINT fk_payments_order_buyer
        FOREIGN KEY (order_id, buyer_id)
        REFERENCES orders (id, buyer_id);

ALTER TABLE refunds
    ADD CONSTRAINT fk_refunds_payment_order
        FOREIGN KEY (payment_id, order_id)
        REFERENCES payments (id, order_id);

ALTER TABLE refunds
    ADD CONSTRAINT fk_refunds_seller_order_order
        FOREIGN KEY (seller_order_id, order_id)
        REFERENCES seller_orders (id, order_id);

ALTER TABLE inventory_reservations
    ADD CONSTRAINT fk_inventory_reservations_item_product
        FOREIGN KEY (order_item_id, product_id)
        REFERENCES order_items (id, product_id);
