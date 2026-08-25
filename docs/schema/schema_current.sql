-- LucentFlow current-state schema snapshot.
--
-- Mirrors Flyway baseline: lucentflow-api/src/main/resources/db/migration/V1__init_schema.sql
-- Prefer that file for runtime; this copy is for docs / review only.
-- Seed / demo tenants: lucentflow-api/src/main/resources/db/demo_setup.sql
--
-- @author ArchLucent
-- @since 1.2

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ---------------------------------------------------------------------------
-- Global chain facts
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS whale_transactions (
    id                      BIGSERIAL PRIMARY KEY,
    hash                    VARCHAR(66) UNIQUE NOT NULL,
    from_address            VARCHAR(42) NOT NULL,
    to_address              VARCHAR(42),
    value_eth               DECIMAL(38, 18) NOT NULL,
    block_number            BIGINT NOT NULL,
    timestamp               TIMESTAMPTZ NOT NULL,
    is_contract_creation    BOOLEAN NOT NULL DEFAULT FALSE,
    gas_price               NUMERIC(78, 0),
    gas_limit               NUMERIC(78, 0),
    gas_cost_eth            DECIMAL(38, 18),
    transaction_type        VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    from_address_tag        VARCHAR(100),
    to_address_tag          VARCHAR(100),
    whale_category          VARCHAR(30),
    address_tag             VARCHAR(100),
    transaction_category    VARCHAR(30),
    funding_source_address  VARCHAR(42),
    funding_source_tag      VARCHAR(100),
    rug_risk_level          VARCHAR(20),
    risk_score              INT,
    risk_reasons            JSONB NOT NULL DEFAULT '{}'::jsonb,
    execution_status        VARCHAR(20) DEFAULT 'SUCCESS',
    bytecode_hash           VARCHAR(64),
    token_symbol            VARCHAR(32),
    token_address           VARCHAR(42),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_wt_from_address ON whale_transactions (from_address);
CREATE INDEX IF NOT EXISTS idx_wt_to_address ON whale_transactions (to_address) WHERE to_address IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_wt_block_number ON whale_transactions (block_number);
CREATE INDEX IF NOT EXISTS idx_wt_timestamp ON whale_transactions (timestamp);
CREATE INDEX IF NOT EXISTS idx_wt_value_eth ON whale_transactions (value_eth);
CREATE INDEX IF NOT EXISTS idx_wt_hash ON whale_transactions (hash);
CREATE INDEX IF NOT EXISTS idx_wt_contract_creation
    ON whale_transactions (is_contract_creation) WHERE is_contract_creation = TRUE;
CREATE INDEX IF NOT EXISTS idx_wt_funding_source_address ON whale_transactions (funding_source_address);
CREATE INDEX IF NOT EXISTS idx_wt_rug_risk_level ON whale_transactions (rug_risk_level);
CREATE INDEX IF NOT EXISTS idx_wt_serial_deployer_from_time
    ON whale_transactions (from_address, timestamp DESC)
    WHERE is_contract_creation = TRUE;
CREATE INDEX IF NOT EXISTS idx_wt_bytecode_hash ON whale_transactions (bytecode_hash);
CREATE INDEX IF NOT EXISTS idx_wt_token_address ON whale_transactions (token_address);

DROP TRIGGER IF EXISTS update_whale_transactions_updated_at ON whale_transactions;
CREATE TRIGGER update_whale_transactions_updated_at
    BEFORE UPDATE ON whale_transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE IF NOT EXISTS sync_status (
    id                  BIGSERIAL PRIMARY KEY,
    last_scanned_block  BIGINT NOT NULL,
    sync_status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    chain_head_block    BIGINT,
    block_lag           BIGINT,
    blocks_per_second   DOUBLE PRECISION,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sync_status_singleton CHECK (id = 1)
);

CREATE INDEX IF NOT EXISTS idx_ss_last_scanned_block ON sync_status (last_scanned_block);
CREATE INDEX IF NOT EXISTS idx_ss_sync_status ON sync_status (sync_status);

DROP TRIGGER IF EXISTS update_sync_status_updated_at ON sync_status;
CREATE TRIGGER update_sync_status_updated_at
    BEFORE UPDATE ON sync_status
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TABLE IF NOT EXISTS entity_tags (
    address                 VARCHAR(42) PRIMARY KEY,
    tag_name                VARCHAR(100) NOT NULL,
    category                VARCHAR(32) NOT NULL,
    risk_score_modifier     INTEGER NOT NULL DEFAULT 0,
    metadata                TEXT NOT NULL DEFAULT '{}'
);

CREATE INDEX IF NOT EXISTS idx_entity_tags_category ON entity_tags (category);

CREATE TABLE IF NOT EXISTS funding_edges (
    id                  BIGSERIAL PRIMARY KEY,
    funder_address      VARCHAR(42) NOT NULL,
    funded_address      VARCHAR(42) NOT NULL,
    hop_layer           INT NOT NULL DEFAULT 1,
    related_tx_hash     VARCHAR(66),
    funder_tag          VARCHAR(120),
    blacklisted         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_funding_edges_funded ON funding_edges (funded_address);
CREATE INDEX IF NOT EXISTS idx_funding_edges_funder ON funding_edges (funder_address);
CREATE UNIQUE INDEX IF NOT EXISTS uk_funding_edges_pair_hop
    ON funding_edges (funder_address, funded_address, hop_layer);

CREATE TABLE IF NOT EXISTS projects (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(120) NOT NULL,
    api_key_hash    VARCHAR(64) NOT NULL,
    api_key_prefix  VARCHAR(16) NOT NULL,
    webhook_url     VARCHAR(1024),
    webhook_secret  VARCHAR(256),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    plan            VARCHAR(32) NOT NULL DEFAULT 'BUILDER',
    daily_request_quota INT NOT NULL DEFAULT 2000,
    watchlist_limit INT NOT NULL DEFAULT 50,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_projects_plan CHECK (plan IN ('BUILDER', 'DESK', 'PROTOCOL'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_projects_api_key_hash ON projects (api_key_hash);

CREATE TABLE IF NOT EXISTS watchlist (
    id          BIGSERIAL PRIMARY KEY,
    address     VARCHAR(42) NOT NULL,
    label       VARCHAR(120) NOT NULL,
    category    VARCHAR(40) NOT NULL,
    project_id  BIGINT NOT NULL REFERENCES projects (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_watchlist_project_address ON watchlist (project_id, address);
CREATE INDEX IF NOT EXISTS idx_watchlist_address ON watchlist (address);
CREATE INDEX IF NOT EXISTS idx_watchlist_category ON watchlist (category);
CREATE INDEX IF NOT EXISTS idx_watchlist_project_id ON watchlist (project_id);

CREATE TABLE IF NOT EXISTS alert_rules (
    id                      BIGSERIAL PRIMARY KEY,
    project_id              BIGINT NOT NULL UNIQUE REFERENCES projects (id),
    min_risk_score          INT NOT NULL DEFAULT 70,
    watchlist_only          BOOLEAN NOT NULL DEFAULT FALSE,
    contract_creation_only  BOOLEAN NOT NULL DEFAULT FALSE,
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_alert_rules_project_id ON alert_rules (project_id);

CREATE TABLE IF NOT EXISTS project_api_usage (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT NOT NULL REFERENCES projects (id),
    usage_date      DATE NOT NULL,
    request_count   BIGINT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_project_api_usage_project_date UNIQUE (project_id, usage_date)
);

CREATE INDEX IF NOT EXISTS idx_project_api_usage_project_date
    ON project_api_usage (project_id, usage_date DESC);

CREATE TABLE IF NOT EXISTS worker_leases (
    lease_name  VARCHAR(64) PRIMARY KEY,
    holder_id   VARCHAR(128) NOT NULL,
    lease_until TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_rate_limit_buckets (
    bucket_key    VARCHAR(256) NOT NULL,
    epoch_minute  BIGINT NOT NULL,
    request_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (bucket_key, epoch_minute)
);
