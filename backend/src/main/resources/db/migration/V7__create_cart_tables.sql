-- Shopping cart: one cart per buyer, items from any number of sellers.
-- A cart is NOT a stock reservation; prices and stock are re-validated at checkout.

CREATE TABLE carts (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    buyer_id   BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT pk_carts PRIMARY KEY (id),
    CONSTRAINT uk_carts_buyer UNIQUE (buyer_id),
    CONSTRAINT fk_carts_buyer FOREIGN KEY (buyer_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE cart_items (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    cart_id          BIGINT         NOT NULL,
    product_id       BIGINT         NOT NULL,
    quantity         DECIMAL(12, 3) NOT NULL,
    -- Server price when the item was added, used only to tell the buyer the price has changed since.
    price_when_added DECIMAL(12, 2) NOT NULL,
    created_at       DATETIME(6)    NOT NULL,
    updated_at       DATETIME(6)    NOT NULL,
    CONSTRAINT pk_cart_items PRIMARY KEY (id),
    CONSTRAINT uk_cart_items_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT fk_cart_items_cart FOREIGN KEY (cart_id) REFERENCES carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_cart_items_price CHECK (price_when_added > 0)
);

CREATE INDEX idx_cart_items_product ON cart_items (product_id);
