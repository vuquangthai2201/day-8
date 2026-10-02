CREATE TABLE IF NOT EXISTS wallet (
    id        UUID PRIMARY KEY,
    balance   NUMERIC(19,2) NOT NULL CHECK (balance >= 0),
    locked    BOOLEAN NOT NULL DEFAULT FALSE,
    version   BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS processed_request (
    idempotency_key VARCHAR(100) PRIMARY KEY,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS outbox_event (
    id           UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    type         VARCHAR(50) NOT NULL,
    payload      TEXT NOT NULL,
    published    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
