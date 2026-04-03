-- Bytecode fingerprinting: detect identical contract clones (same creation bytecode).
-- @author ArchLucent

ALTER TABLE whale_transactions
    ADD COLUMN IF NOT EXISTS bytecode_hash VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_wt_bytecode_hash ON whale_transactions (bytecode_hash);

COMMENT ON COLUMN whale_transactions.bytecode_hash IS 'SHA-256 hex of contract creation input data (deploy bytecode fingerprint)';
