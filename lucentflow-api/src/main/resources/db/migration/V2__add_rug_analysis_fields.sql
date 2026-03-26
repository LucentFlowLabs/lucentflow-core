-- Add anti-rug analysis fields to whale_transactions table
-- V2__add_rug_analysis_fields.sql

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
