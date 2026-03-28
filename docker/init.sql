CREATE TABLE IF NOT EXISTS users (
    id         BIGSERIAL PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    full_name  VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS wallets (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT          NOT NULL UNIQUE REFERENCES users(id),
    balance    NUMERIC(19, 4)  NOT NULL,
    version    BIGINT,
    created_at TIMESTAMP       NOT NULL,
    updated_at TIMESTAMP       NOT NULL
);

CREATE TABLE IF NOT EXISTS transactions (
    id               BIGSERIAL PRIMARY KEY,
    from_wallet_id   BIGINT,                   -- nullable: NULL for DEPOSIT (external funds)
    to_wallet_id     BIGINT         NOT NULL,
    amount           NUMERIC(19, 4) NOT NULL,
    status           VARCHAR(10)    NOT NULL,
    idempotency_key  VARCHAR(64)    UNIQUE,
    failure_reason   VARCHAR(500),
    created_at       TIMESTAMP      NOT NULL
);
