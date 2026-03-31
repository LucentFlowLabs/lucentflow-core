-- Enterprise observability: chain head, lag, and throughput for ID=1 sync row.
-- @author ArchLucent

ALTER TABLE sync_status
    ADD COLUMN IF NOT EXISTS chain_head_block BIGINT,
    ADD COLUMN IF NOT EXISTS block_lag BIGINT,
    ADD COLUMN IF NOT EXISTS blocks_per_second DOUBLE PRECISION;

COMMENT ON COLUMN sync_status.chain_head_block IS 'Latest chain tip block height at last heartbeat';
COMMENT ON COLUMN sync_status.block_lag IS 'chain_head_block - last_scanned_block (>= 0)';
COMMENT ON COLUMN sync_status.blocks_per_second IS 'Approximate indexing throughput since previous heartbeat';
