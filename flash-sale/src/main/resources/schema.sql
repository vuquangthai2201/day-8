CREATE TABLE IF NOT EXISTS product (
    id    BIGINT PRIMARY KEY,
    name  VARCHAR(200) NOT NULL,
    price NUMERIC(19,2) NOT NULL,
    stock INT NOT NULL CHECK (stock >= 0)
);

CREATE TABLE IF NOT EXISTS orders (
    id              UUID PRIMARY KEY,
    user_id         VARCHAR(64) NOT NULL,
    product_id      BIGINT NOT NULL REFERENCES product(id),
    qty             INT NOT NULL CHECK (qty > 0),
    amount          NUMERIC(19,2) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_orders_pending ON orders (created_at) WHERE status = 'PENDING';

CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_user_product ON orders (user_id, product_id) WHERE status IN ('PENDING', 'PAID');

INSERT INTO product (id, name, price, stock) VALUES (1, 'Flash Sale Sneaker', 99.00, 100) ON CONFLICT (id) DO NOTHING;
