-- Enforce sync_status ID=1 singleton protocol and consolidate stray rows.
DO $$
DECLARE
    v_last_block BIGINT := 0;
    v_chain_head BIGINT;
    v_block_lag BIGINT;
    v_bps DOUBLE PRECISION;
    v_created TIMESTAMPTZ;
    v_updated TIMESTAMPTZ;
BEGIN
    SELECT COALESCE(MAX(last_scanned_block), 0),
           MAX(chain_head_block),
           MAX(block_lag),
           MAX(blocks_per_second),
           MIN(created_at),
           MAX(updated_at)
    INTO v_last_block, v_chain_head, v_block_lag, v_bps, v_created, v_updated
    FROM sync_status;

    DELETE FROM sync_status;

    INSERT INTO sync_status (
            id,
            last_scanned_block,
            sync_status,
            chain_head_block,
            block_lag,
            blocks_per_second,
            created_at,
            updated_at
    )
    VALUES (
            1,
            v_last_block,
            'ACTIVE',
            v_chain_head,
            v_block_lag,
            v_bps,
            COALESCE(v_created, CURRENT_TIMESTAMP),
            COALESCE(v_updated, CURRENT_TIMESTAMP)
    );
END $$;

ALTER TABLE sync_status DROP CONSTRAINT IF EXISTS chk_sync_status_singleton;
ALTER TABLE sync_status ADD CONSTRAINT chk_sync_status_singleton CHECK (id = 1);
