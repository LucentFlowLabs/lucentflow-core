-- Out-of-process webhook dead-letter: bulkhead-deferred deliveries survive worker restart.
-- Capacity is enforced by the writer (COUNT < lucentflow.webhook.dead-letter-capacity).
-- Secrets are not stored; drain reloads project webhook_secret (else global token).

CREATE TABLE webhook_dead_letters (
    id                   BIGSERIAL PRIMARY KEY,
    tx_hash              VARCHAR(66) NOT NULL,
    project_id           BIGINT,
    webhook_url          VARCHAR(1024) NOT NULL,
    watchlist_hit        BOOLEAN NOT NULL DEFAULT FALSE,
    watchlist_label      VARCHAR(120),
    watchlist_category   VARCHAR(40),
    watchlist_address    VARCHAR(42),
    payload_json         JSONB NOT NULL DEFAULT '{}'::jsonb,
    attempts             INT NOT NULL DEFAULT 1,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_webhook_dead_letters_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_webhook_dead_letters_drain ON webhook_dead_letters (id);

COMMENT ON TABLE webhook_dead_letters IS
    'Webhook deliveries deferred after bulkhead saturation; polled with FOR UPDATE SKIP LOCKED';
COMMENT ON COLUMN webhook_dead_letters.payload_json IS
    'Snapshot of whale hash / risk fields used to rebuild the outbound JSON';
COMMENT ON COLUMN webhook_dead_letters.attempts IS
    'DLQ cycles already used; writer drops the row when this exceeds max-attempts';
