-- Multi-Asset Intelligence (ERC-20 outpost): optional token metadata on whale rows.
-- @author ArchLucent

ALTER TABLE whale_transactions
    ADD COLUMN IF NOT EXISTS token_symbol VARCHAR(32),
    ADD COLUMN IF NOT EXISTS token_address VARCHAR(42);

CREATE INDEX IF NOT EXISTS idx_wt_token_address ON whale_transactions (token_address);

COMMENT ON COLUMN whale_transactions.token_symbol IS 'Core ERC-20 symbol when transaction_type is ERC20_TRANSFER';
COMMENT ON COLUMN whale_transactions.token_address IS 'ERC-20 contract address (checksummed or lower)';
