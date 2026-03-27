-- LucentFlow Core - Add execution_status column
-- PostgreSQL 16 compatible with T10 standards
--
-- Adds Transaction Integrity Audit field with idempotency check.
--
-- @author ArchLucent
-- @since 1.0

-- Add execution_status column with idempotency check
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'whale_transactions' 
        AND column_name = 'execution_status'
    ) THEN
        ALTER TABLE whale_transactions ADD COLUMN execution_status VARCHAR(20) DEFAULT 'SUCCESS';
    END IF;
END $$;
