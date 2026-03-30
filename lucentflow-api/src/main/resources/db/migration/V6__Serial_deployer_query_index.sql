-- Partial index for serial deployer / factory detection: COUNT by from_address in a time window (contract creations only).
-- Aligns with WhaleTransactionRepository.countRecentDeployments.
CREATE INDEX IF NOT EXISTS idx_wt_serial_deployer_from_time
    ON whale_transactions (from_address, timestamp DESC)
    WHERE is_contract_creation = TRUE;
