-- LucentFlow Core - Initial Schema Migration
-- PostgreSQL 16 compatible with T10 standards
-- Whale transaction tracking and blockchain synchronization

-- Create whale_transactions table
CREATE TABLE IF NOT EXISTS whale_transactions (
    id BIGSERIAL PRIMARY KEY,
    hash VARCHAR(66) UNIQUE NOT NULL,
    from_address VARCHAR(42) NOT NULL,
    to_address VARCHAR(42) NULL, -- NULL for contract deployments
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

-- Create sync_status table
CREATE TABLE IF NOT EXISTS sync_status (
    id BIGSERIAL PRIMARY KEY,
    last_scanned_block BIGINT NOT NULL,
    sync_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for whale_transactions table (PostgreSQL 16 optimized)
CREATE INDEX IF NOT EXISTS idx_wt_from_address ON whale_transactions(from_address);
CREATE INDEX IF NOT EXISTS idx_wt_to_address ON whale_transactions(to_address) WHERE to_address IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_wt_block_number ON whale_transactions(block_number);
CREATE INDEX IF NOT EXISTS idx_wt_timestamp ON whale_transactions(timestamp);
CREATE INDEX IF NOT EXISTS idx_wt_value_eth ON whale_transactions(value_eth);
CREATE INDEX IF NOT EXISTS idx_wt_hash ON whale_transactions(hash);
CREATE INDEX IF NOT EXISTS idx_wt_contract_creation ON whale_transactions(is_contract_creation) WHERE is_contract_creation = TRUE;

-- Create indexes for sync_status table
CREATE INDEX IF NOT EXISTS idx_ss_last_scanned_block ON sync_status(last_scanned_block);
CREATE INDEX IF NOT EXISTS idx_ss_sync_status ON sync_status(sync_status);

-- Create trigger for updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_whale_transactions_updated_at BEFORE UPDATE
    ON whale_transactions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_sync_status_updated_at BEFORE UPDATE
    ON sync_status FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert initial sync status record
INSERT INTO sync_status (last_scanned_block, sync_status) 
VALUES (0, 'ACTIVE') 
ON CONFLICT DO NOTHING;

-- Add comments for documentation
COMMENT ON TABLE whale_transactions IS 'Stores large Ethereum transactions (>10 ETH) with comprehensive analysis data';
COMMENT ON TABLE sync_status IS 'Tracks blockchain synchronization status and last processed block';
COMMENT ON COLUMN whale_transactions.to_address IS 'NULL for contract deployments, populated for regular transfers';
COMMENT ON COLUMN whale_transactions.value_eth IS 'Transaction value in ETH with 18 decimal precision';
COMMENT ON COLUMN whale_transactions.gas_price IS 'Gas price in wei (BigInteger equivalent)';
COMMENT ON COLUMN whale_transactions.gas_limit IS 'Gas limit in wei (BigInteger equivalent)';
