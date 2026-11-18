CREATE TABLE shop_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    slug VARCHAR(80) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX shop_categories_created_idx ON shop_categories (created_at DESC, id DESC);

CREATE TABLE shop_products (
    id UUID PRIMARY KEY,
    category_id UUID NOT NULL REFERENCES shop_categories(id),
    sku VARCHAR(64) NOT NULL UNIQUE CHECK (sku ~ '^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$'),
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    description VARCHAR(2000) NOT NULL,
    image_url VARCHAR(2048) NOT NULL,
    price BIGINT NOT NULL CHECK (price BETWEEN 1 AND 1000000000000),
    stock BIGINT NOT NULL CHECK (stock >= 0),
    active BOOLEAN NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX shop_products_created_idx ON shop_products (created_at DESC, id DESC);
CREATE INDEX shop_products_category_idx ON shop_products (category_id, created_at DESC, id DESC);

CREATE TABLE shop_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id),
    request_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('pending', 'confirmed', 'shipped', 'cancelled')),
    total BIGINT NOT NULL CHECK (total >= 0),
    recipient_name VARCHAR(160) NOT NULL,
    phone_number VARCHAR(13) NOT NULL CHECK (phone_number ~ '^\+989[0-9]{9}$'),
    province VARCHAR(80) NOT NULL,
    city VARCHAR(80) NOT NULL,
    address_line VARCHAR(500) NOT NULL,
    postal_code VARCHAR(10) NOT NULL CHECK (postal_code ~ '^[0-9]{10}$'),
    carrier VARCHAR(80),
    tracking_code VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, request_id),
    CHECK ((status = 'shipped' AND carrier IS NOT NULL AND tracking_code IS NOT NULL)
        OR (status <> 'shipped' AND carrier IS NULL AND tracking_code IS NULL))
);
CREATE INDEX shop_orders_user_created_idx ON shop_orders (user_id, created_at DESC, id DESC);
CREATE INDEX shop_orders_created_idx ON shop_orders (created_at DESC, id DESC);
CREATE INDEX shop_orders_status_created_idx ON shop_orders (status, created_at DESC, id DESC);

CREATE TABLE shop_order_items (
    order_id UUID NOT NULL REFERENCES shop_orders(id),
    product_id UUID NOT NULL REFERENCES shop_products(id),
    position SMALLINT NOT NULL CHECK (position BETWEEN 0 AND 49),
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    unit_price BIGINT NOT NULL CHECK (unit_price BETWEEN 1 AND 1000000000000),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 100),
    line_total BIGINT NOT NULL CHECK (line_total = unit_price * quantity),
    PRIMARY KEY (order_id, product_id),
    UNIQUE (order_id, position)
);
CREATE INDEX shop_order_items_product_idx ON shop_order_items (product_id);
