-- LucentFlow Core - Initial Schema Migration (H2 Compatible)
-- H2 Database compatible version for tests
-- Whale transaction tracking and blockchain synchronization

-- Create whale_transactions table
CREATE TABLE IF NOT EXISTS whale_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    last_scanned_block BIGINT NOT NULL,
    sync_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for whale_transactions table (H2 compatible - no WHERE clauses)
CREATE INDEX IF NOT EXISTS idx_wt_from_address ON whale_transactions(from_address);
CREATE INDEX IF NOT EXISTS idx_wt_to_address ON whale_transactions(to_address);
CREATE INDEX IF NOT EXISTS idx_wt_block_number ON whale_transactions(block_number);
CREATE INDEX IF NOT EXISTS idx_wt_timestamp ON whale_transactions(timestamp);
CREATE INDEX IF NOT EXISTS idx_wt_value_eth ON whale_transactions(value_eth);
CREATE INDEX IF NOT EXISTS idx_wt_hash ON whale_transactions(hash);
CREATE INDEX IF NOT EXISTS idx_wt_contract_creation ON whale_transactions(is_contract_creation);

-- Create indexes for sync_status table
CREATE INDEX IF NOT EXISTS idx_ss_last_scanned_block ON sync_status(last_scanned_block);
CREATE INDEX IF NOT EXISTS idx_ss_sync_status ON sync_status(sync_status);

-- Insert initial sync status record
INSERT INTO sync_status (last_scanned_block, sync_status) 
VALUES (0, 'ACTIVE');
