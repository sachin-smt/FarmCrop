-- Crop catalog: categories, seller product listings and product images.
-- Stock quantities live in `inventory` (V6), the single source of truth for availability.

CREATE TABLE categories (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(80)  NOT NULL,
    slug          VARCHAR(90)  NOT NULL,
    description   VARCHAR(500) NULL,
    image_ref     VARCHAR(255) NULL,
    parent_id     BIGINT       NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    display_order INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name),
    CONSTRAINT uk_categories_slug UNIQUE (slug),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
);

CREATE INDEX idx_categories_active_order ON categories (is_active, display_order);

-- Reference data matching the existing frontend category bar (frontend/src/data.ts).
-- "Organic" is modelled as products.organic (a growing practice), not as a category.
INSERT INTO categories (name, slug, description, display_order, created_at, updated_at) VALUES ('Grains', 'grains', 'Wheat, rice, maize and other cereal grains', 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
INSERT INTO categories (name, slug, description, display_order, created_at, updated_at) VALUES ('Vegetables', 'vegetables', 'Fresh vegetables', 2, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
INSERT INTO categories (name, slug, description, display_order, created_at, updated_at) VALUES ('Fruits', 'fruits', 'Fresh fruits', 3, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
INSERT INTO categories (name, slug, description, display_order, created_at, updated_at) VALUES ('Pulses', 'pulses', 'Lentils, gram and other pulses', 4, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
INSERT INTO categories (name, slug, description, display_order, created_at, updated_at) VALUES ('Oilseeds', 'oilseeds', 'Mustard, groundnut, soybean and other oilseeds', 5, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
INSERT INTO categories (name, slug, description, display_order, created_at, updated_at) VALUES ('Spices', 'spices', 'Chilli, turmeric, coriander and other spices', 6, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
INSERT INTO categories (name, slug, description, display_order, created_at, updated_at) VALUES ('Millets', 'millets', 'Bajra, jowar, ragi and other millets', 7, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
INSERT INTO categories (name, slug, description, display_order, created_at, updated_at) VALUES ('Dairy', 'dairy', 'Milk and dairy products', 8, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));

CREATE TABLE products (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    seller_id           BIGINT         NOT NULL,
    category_id         BIGINT         NOT NULL,
    name                VARCHAR(150)   NOT NULL,
    description         VARCHAR(4000)  NULL,
    -- KG, QUINTAL, TONNE, PIECE, BUNDLE
    unit                VARCHAR(20)    NOT NULL,
    price               DECIMAL(12, 2) NOT NULL,
    max_order_quantity  DECIMAL(12, 3) NULL,
    organic             BOOLEAN        NOT NULL DEFAULT FALSE,
    harvest_date        DATE           NULL,
    available_from      DATE           NULL,
    location_text       VARCHAR(150)   NULL,
    location_district   VARCHAR(100)   NULL,
    location_state      VARCHAR(100)   NULL,
    -- DRAFT, ACTIVE, INACTIVE, OUT_OF_STOCK, UNDER_REVIEW, SUSPENDED, ARCHIVED
    status              VARCHAR(20)    NOT NULL,
    -- Optional moderation marker (e.g. PRICE_ANOMALY) for manual admin review; never an automatic verdict.
    moderation_flag     VARCHAR(30)    NULL,
    moderation_reason   VARCHAR(1000)  NULL,
    moderated_by        BIGINT         NULL,
    moderated_at        DATETIME(6)    NULL,
    published_at        DATETIME(6)    NULL,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT fk_products_seller FOREIGN KEY (seller_id) REFERENCES seller_profiles (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_moderator FOREIGN KEY (moderated_by) REFERENCES users (id),
    CONSTRAINT ck_products_price CHECK (price > 0),
    CONSTRAINT ck_products_max_order CHECK (max_order_quantity IS NULL OR max_order_quantity > 0),
    CONSTRAINT ck_products_unit CHECK (unit IN ('KG', 'QUINTAL', 'TONNE', 'PIECE', 'BUNDLE'))
);

-- Marketplace browsing: active listings by category / price / recency; seller dashboards; moderation queue.
CREATE INDEX idx_products_seller_status ON products (seller_id, status);
CREATE INDEX idx_products_status_category ON products (status, category_id);
CREATE INDEX idx_products_status_price ON products (status, price);
CREATE INDEX idx_products_status_published ON products (status, published_at);
CREATE INDEX idx_products_location ON products (location_state, location_district);
CREATE INDEX idx_products_name ON products (name);
CREATE INDEX idx_products_moderation_flag ON products (moderation_flag);

-- Public product photos (separate from private seller identity documents).
CREATE TABLE product_images (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    product_id    BIGINT       NOT NULL,
    storage_key   VARCHAR(255) NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    width_px      INT          NULL,
    height_px     INT          NULL,
    display_order INT          NOT NULL DEFAULT 0,
    is_primary    BOOLEAN      NOT NULL DEFAULT FALSE,
    alt_text      VARCHAR(200) NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_product_images PRIMARY KEY (id),
    CONSTRAINT uk_product_images_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_product_images_size CHECK (size_bytes > 0),
    CONSTRAINT ck_product_images_order CHECK (display_order >= 0)
);

CREATE INDEX idx_product_images_product_order ON product_images (product_id, display_order);
