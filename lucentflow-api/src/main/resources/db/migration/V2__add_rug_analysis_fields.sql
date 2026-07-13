-- Add anti-rug analysis fields to whale_transactions table
-- V2__add_rug_analysis_fields.sql

-- Bootstrap guard:
-- If schema was baselined at V1 on an empty database, V1 DDL is skipped and
-- whale_transactions/sync_status are missing. Create core tables defensively.
CREATE TABLE IF NOT EXISTS whale_transactions (
    id BIGSERIAL PRIMARY KEY,
    hash VARCHAR(66) UNIQUE NOT NULL,
    from_address VARCHAR(42) NOT NULL,
    to_address VARCHAR(42) NULL,
    value_eth DECIMAL(38,18) NOT NULL,
    block_number BIGINT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    is_contract_creation BOOLEAN NOT NULL DEFAULT FALSE,
    gas_price NUMERIC(78,0) NULL,
    gas_limit NUMERIC(78,0) NULL,
    gas_cost_eth DECIMAL(38,18) NULL,
    transaction_type VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    from_address_tag VARCHAR(50) NULL,
    to_address_tag VARCHAR(20) NULL,
    whale_category VARCHAR(30) NULL,
    address_tag VARCHAR(50) NULL,
    transaction_category VARCHAR(30) NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sync_status (
    id BIGSERIAL PRIMARY KEY,
    last_scanned_block BIGINT NOT NULL,
    sync_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

DROP TRIGGER IF EXISTS update_whale_transactions_updated_at ON whale_transactions;
CREATE TRIGGER update_whale_transactions_updated_at
BEFORE UPDATE ON whale_transactions
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_sync_status_updated_at ON sync_status;
CREATE TRIGGER update_sync_status_updated_at
BEFORE UPDATE ON sync_status
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

INSERT INTO sync_status (id, last_scanned_block, sync_status)
VALUES (1, 0, 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

-- PostgreSQL doesn't support IF NOT EXISTS for ADD COLUMN, so we need to check first
DO $$
BEGIN
    -- Add funding_source_address if it doesn't exist
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'whale_transactions' 
        AND column_name = 'funding_source_address'
    ) THEN
        ALTER TABLE whale_transactions ADD COLUMN funding_source_address VARCHAR(42);
    END IF;

    -- Add funding_source_tag if it doesn't exist
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'whale_transactions' 
        AND column_name = 'funding_source_tag'
    ) THEN
        ALTER TABLE whale_transactions ADD COLUMN funding_source_tag VARCHAR(100);
    END IF;

    -- Add rug_risk_level if it doesn't exist
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'whale_transactions' 
        AND column_name = 'rug_risk_level'
    ) THEN
        ALTER TABLE whale_transactions ADD COLUMN rug_risk_level VARCHAR(20);
    END IF;
END $$;

-- Add indexes for new anti-rug fields (H2 compatible)
CREATE INDEX IF NOT EXISTS idx_wt_funding_source_address ON whale_transactions(funding_source_address);
CREATE INDEX IF NOT EXISTS idx_wt_rug_risk_level ON whale_transactions(rug_risk_level);
